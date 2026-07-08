package com.movie.scanner.ui.scanbulk

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.BarcodeDecoder
import com.google.mlkit.vision.barcode.BarcodeScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ScanBulkCaptureEvent {
    data object NavigateToQueue : ScanBulkCaptureEvent

    data object NavigateToLoading : ScanBulkCaptureEvent

    data object NavigateToReview : ScanBulkCaptureEvent
}

data class ScanBulkCaptureUiState(
    val captureMode: ScanCaptureMode = ScanCaptureMode.BARCODE,
    val isConfigured: Boolean = false,
    val isCapturing: Boolean = false,
    val isProcessingCapture: Boolean = false,
    val pairCount: Int = 0,
    val currentPairNumber: Int = 1,
    val statusMessage: String = "Take a photo of the barcode",
    val captureErrorMessage: String? = null,
    val bulkLocation: String = "",
    val showLocationDialog: Boolean = false,
    val bulkDiscType: String? = null,
    val showDiscTypeDialog: Boolean = false,
    val isRescanMode: Boolean = false,
)

@HiltViewModel
class ScanBulkCaptureViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val bulkImageRepository: BulkImageRepository,
    private val scanSessionHolder: ScanSessionHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScanBulkCaptureUiState(
            isConfigured = apiKeyStore.hasMinimumConfiguration(),
            bulkLocation = scanSessionHolder.bulkBatchLocation.ifBlank {
                scanSessionHolder.lastReviewLocation
            },
            bulkDiscType = resolveBulkCaptureDiscType(),
        ),
    )
    val uiState: StateFlow<ScanBulkCaptureUiState> = _uiState.asStateFlow()
    private val navigationEvents = Channel<ScanBulkCaptureEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    private var barcodeScanner: BarcodeScanner? = null
    private var pendingBarcodeBitmap: Bitmap? = null
    private var rescanRecordId: Long? = null
    private var rescanCompletionInProgress = false

    override fun onCleared() {
        barcodeScanner?.close()
        barcodeScanner = null
        super.onCleared()
    }

    fun refreshConfiguration() {
        _uiState.update { it.copy(isConfigured = apiKeyStore.hasMinimumConfiguration()) }
    }

    fun prepareScreen() {
        refreshConfiguration()
        pendingBarcodeBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        pendingBarcodeBitmap = null
        rescanRecordId = scanSessionHolder.resolveBulkRescanRecordId()
        val isRescanMode = rescanRecordId != null
        _uiState.value = ScanBulkCaptureUiState(
            isConfigured = apiKeyStore.hasMinimumConfiguration(),
            pairCount = if (isRescanMode) 0 else _uiState.value.pairCount,
            currentPairNumber = if (isRescanMode) 1 else _uiState.value.pairCount + 1,
            bulkLocation = scanSessionHolder.bulkBatchLocation.ifBlank {
                scanSessionHolder.lastReviewLocation
            },
            bulkDiscType = resolveBulkCaptureDiscType(),
            isRescanMode = isRescanMode,
            statusMessage = if (isRescanMode) {
                "Rescan the barcode"
            } else {
                "Take a photo of the barcode"
            },
        )
    }

    /**
     * Aborts an in-progress bulk rescan and returns to review without changing stored images.
     */
    fun cancelRescan() {
        if (!_uiState.value.isRescanMode) {
            finishScanning()
            return
        }
        scanSessionHolder.clearBulkRescan()
        rescanRecordId = null
        viewModelScope.launch {
            navigationEvents.send(ScanBulkCaptureEvent.NavigateToReview)
        }
    }

    /**
     * Opens the bulk location prompt with the current saved location as the draft default.
     */
    fun openLocationDialog() {
        _uiState.update { it.copy(showLocationDialog = true) }
    }

    /**
     * Closes the location prompt without persisting draft edits.
     */
    fun dismissLocationDialog() {
        _uiState.update { it.copy(showLocationDialog = false) }
    }

    /**
     * Saves the bulk location for later review forms and shows it on the capture header.
     */
    fun saveBulkLocation(location: String) {
        scanSessionHolder.rememberBulkBatchLocation(location)
        _uiState.update {
            it.copy(
                bulkLocation = location,
                showLocationDialog = false,
            )
        }
    }

    /**
     * Opens the bulk disc type picker.
     */
    fun openDiscTypeDialog() {
        _uiState.update { it.copy(showDiscTypeDialog = true) }
    }

    /**
     * Closes the disc type picker without persisting a selection.
     */
    fun dismissDiscTypeDialog() {
        _uiState.update { it.copy(showDiscTypeDialog = false) }
    }

    /**
     * Saves the bulk disc type for later review forms and shows it on the capture header.
     */
    fun saveBulkDiscType(discType: String?) {
        scanSessionHolder.rememberBulkBatchDiscType(discType)
        _uiState.update {
            it.copy(
                bulkDiscType = discType,
                showDiscTypeDialog = false,
            )
        }
    }

    fun beginCaptureProcessing() {
        _uiState.update {
            it.copy(
                isCapturing = true,
                isProcessingCapture = false,
                captureErrorMessage = null,
            )
        }
    }

    fun clearCaptureError() {
        _uiState.update {
            it.copy(
                captureErrorMessage = null,
                isCapturing = false,
                isProcessingCapture = false,
            )
        }
    }

    fun processCapturedImage(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                isProcessingCapture = true,
            )
        }
        viewModelScope.launch {
            try {
                when (_uiState.value.captureMode) {
                    ScanCaptureMode.BARCODE -> onBarcodeCaptured(bitmap)
                    ScanCaptureMode.COVER -> onCoverCaptured(bitmap)
                }
            } catch (exception: Exception) {
                onCaptureFailed(
                    exception.message ?: "Could not process the captured image.",
                )
            }
        }
    }

    private fun onBarcodeCaptured(bitmap: Bitmap) {
        pendingBarcodeBitmap?.let { previousBitmap ->
            if (!previousBitmap.isRecycled) {
                previousBitmap.recycle()
            }
        }
        pendingBarcodeBitmap = bitmap
        val isRescanMode = _uiState.value.isRescanMode
        _uiState.update {
            it.copy(
                captureMode = ScanCaptureMode.COVER,
                statusMessage = if (isRescanMode) {
                    "Rescan the cover"
                } else {
                    "Capture the cover photo"
                },
                captureErrorMessage = null,
                isProcessingCapture = false,
            )
        }
    }

    private fun onCoverCaptured(bitmap: Bitmap) {
        val barcodeBitmap = pendingBarcodeBitmap
        if (barcodeBitmap == null) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            _uiState.update {
                it.copy(
                    captureErrorMessage = "Barcode photo is missing. Capture the barcode again.",
                    captureMode = ScanCaptureMode.BARCODE,
                    statusMessage = if (it.isRescanMode) {
                        "Rescan the barcode"
                    } else {
                        "Take a photo of the barcode"
                    },
                    isProcessingCapture = false,
                )
            }
            return
        }
        pendingBarcodeBitmap = null
        if (_uiState.value.isRescanMode) {
            completeRescanCapture(
                barcodeBitmap = barcodeBitmap,
                coverBitmap = bitmap,
            )
            return
        }
        bulkImageRepository.enqueueCapturedPair(
            barcodeBitmap = barcodeBitmap,
            coverBitmap = bitmap,
            onFailure = { message ->
                _uiState.update { state ->
                    state.copy(captureErrorMessage = message)
                }
            },
        )
        val nextPairCount = _uiState.value.pairCount + 1
        _uiState.update {
            it.copy(
                captureMode = ScanCaptureMode.BARCODE,
                pairCount = nextPairCount,
                currentPairNumber = nextPairCount + 1,
                statusMessage = "Take a photo of the barcode",
                captureErrorMessage = null,
                isProcessingCapture = false,
            )
        }
    }

    fun onCaptureFailed(message: String) {
        _uiState.update {
            it.copy(
                captureErrorMessage = message,
                isCapturing = false,
                isProcessingCapture = false,
            )
        }
    }

    /**
     * Clears a stale capture overlay when the capture screen becomes visible again.
     */
    fun onCaptureScreenResumed() {
        if (rescanCompletionInProgress) {
            return
        }
        if (_uiState.value.isCapturing || _uiState.value.isProcessingCapture) {
            _uiState.update {
                it.copy(
                    isCapturing = false,
                    isProcessingCapture = false,
                )
            }
        }
    }

    fun finishScanning() {
        viewModelScope.launch {
            navigationEvents.send(ScanBulkCaptureEvent.NavigateToQueue)
        }
    }

    private fun completeRescanCapture(
        barcodeBitmap: Bitmap,
        coverBitmap: Bitmap,
    ) {
        val recordId = rescanRecordId
        if (recordId == null) {
            if (!barcodeBitmap.isRecycled) {
                barcodeBitmap.recycle()
            }
            if (!coverBitmap.isRecycled) {
                coverBitmap.recycle()
            }
            _uiState.update {
                it.copy(
                    captureErrorMessage = "Rescan session expired. Return to review and try again.",
                    captureMode = ScanCaptureMode.BARCODE,
                    statusMessage = "Rescan the barcode",
                    isProcessingCapture = false,
                )
            }
            return
        }
        rescanCompletionInProgress = true
        viewModelScope.launch {
            try {
                val updatedRecord = bulkImageRepository.replaceCapturedPair(
                    recordId = recordId,
                    barcodeBitmap = barcodeBitmap,
                    coverBitmap = coverBitmap,
                )
                prepareRescanSession(
                    recordId = recordId,
                    barcodeRelativeFilepath = updatedRecord.barcodeRelFilepath,
                    coverRelativeFilepath = updatedRecord.coverRelFilepath,
                )
                scanSessionHolder.clearBulkRescan()
                rescanRecordId = null
                _uiState.update { it.copy(isProcessingCapture = false) }
                navigationEvents.send(ScanBulkCaptureEvent.NavigateToLoading)
            } catch (exception: Exception) {
                onCaptureFailed(
                    exception.message ?: "Could not save the rescanned images.",
                )
            } finally {
                rescanCompletionInProgress = false
            }
        }
    }

    private suspend fun prepareRescanSession(
        recordId: Long,
        barcodeRelativeFilepath: String,
        coverRelativeFilepath: String,
    ) {
        val barcodeBitmap = bulkImageRepository.loadBitmap(barcodeRelativeFilepath)
        val coverBitmap = bulkImageRepository.loadBitmap(coverRelativeFilepath)
        if (barcodeBitmap == null || coverBitmap == null) {
            throw IllegalStateException("Rescanned images could not be loaded.")
        }
        scanSessionHolder.startNewScan()
        scanSessionHolder.startBulkItem(
            recordId = recordId,
            coverRelFilepath = coverRelativeFilepath,
        )
        val decodedBarcode = withContext(Dispatchers.Default) {
            BarcodeDecoder.decodeFromBitmap(
                bitmap = barcodeBitmap,
                barcodeScanner = resolveBarcodeScanner(),
            )
        }
        if (!decodedBarcode.isNullOrBlank()) {
            scanSessionHolder.recordBarcodeCapture(decodedBarcode, barcodeBitmap)
        } else {
            scanSessionHolder.recordBarcodeCaptureForBulk(barcodeBitmap)
        }
        scanSessionHolder.recordCoverCapture(coverBitmap)
    }

    private fun resolveBarcodeScanner(): BarcodeScanner {
        val existingScanner = barcodeScanner
        if (existingScanner != null) {
            return existingScanner
        }
        val createdScanner = BarcodeDecoder.buildBarcodeScanner()
        barcodeScanner = createdScanner
        return createdScanner
    }

    private fun resolveBulkCaptureDiscType(): String? {
        val batchDiscType = scanSessionHolder.bulkBatchDiscType
        if (!batchDiscType.isNullOrBlank()) {
            return batchDiscType
        }

        return scanSessionHolder.lastReviewDiscType?.takeIf { discType -> discType.isNotBlank() }
    }
}
