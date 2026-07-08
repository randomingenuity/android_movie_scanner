package com.movie.scanner.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.ScanCaptureMode
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

sealed interface CaptureProcessingEvent {
    data object NavigateToLoading : CaptureProcessingEvent
}

data class ScanUiState(
    val captureMode: ScanCaptureMode = ScanCaptureMode.BARCODE,
    val isConfigured: Boolean = false,
    val isProcessingCapture: Boolean = false,
    val showManualEntryDialog: Boolean = false,
    val manualEntryError: String? = null,
    val statusMessage: String = "Take a photo of the barcode",
    val captureErrorMessage: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val scanSessionHolder: ScanSessionHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState(isConfigured = apiKeyStore.hasMinimumConfiguration()))
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    private val barcodeScanner: BarcodeScanner = BarcodeDecoder.buildBarcodeScanner()
    private val captureProcessingEvents = Channel<CaptureProcessingEvent>(Channel.BUFFERED)
    val captureProcessingEventFlow = captureProcessingEvents.receiveAsFlow()

    override fun onCleared() {
        barcodeScanner.close()
        super.onCleared()
    }

    fun refreshConfiguration() {
        _uiState.update { it.copy(isConfigured = apiKeyStore.hasMinimumConfiguration()) }
    }

    fun prepareScreen() {
        refreshConfiguration()
        if (scanSessionHolder.consumeCoverRetakeRequest()) {
            resumeCoverCapture()
        } else {
            beginScanSession()
        }
    }

    fun beginScanSession() {
        scanSessionHolder.startNewScan()
        _uiState.value = ScanUiState(
            isConfigured = apiKeyStore.hasMinimumConfiguration(),
            captureMode = ScanCaptureMode.BARCODE,
            statusMessage = "Take a photo of the barcode",
        )
    }

    fun resumeCoverCapture() {
        _uiState.update {
            it.copy(
                captureMode = ScanCaptureMode.COVER,
                statusMessage = "Capture the cover photo",
                captureErrorMessage = null,
                isProcessingCapture = false,
            )
        }
    }

    /**
     * Clears a stale processing overlay when the capture screen becomes visible again.
     */
    fun onCaptureScreenResumed() {
        if (scanSessionHolder.consumeCoverRetakeRequest()) {
            resumeCoverCapture()
            return
        }
        if (_uiState.value.isProcessingCapture) {
            _uiState.update { it.copy(isProcessingCapture = false) }
        }
    }

    fun consumeAddedMessage(): String? = scanSessionHolder.consumeLastAddedTitle()

    fun beginCaptureProcessing() {
        _uiState.update {
            it.copy(
                isProcessingCapture = true,
                captureErrorMessage = null,
            )
        }
    }

    fun clearCaptureError() {
        _uiState.update {
            it.copy(
                captureErrorMessage = null,
                isProcessingCapture = false,
            )
        }
    }

    fun skipBarcode() {
        if (_uiState.value.captureMode != ScanCaptureMode.BARCODE) {
            return
        }
        scanSessionHolder.skipBarcode()
        _uiState.update {
            it.copy(
                captureMode = ScanCaptureMode.COVER,
                statusMessage = "Capture the cover photo",
                captureErrorMessage = null,
            )
        }
    }

    fun processCapturedImage(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                when (_uiState.value.captureMode) {
                    ScanCaptureMode.BARCODE -> {
                        val barcode = withContext(Dispatchers.Default) {
                            BarcodeDecoder.decodeFromBitmap(bitmap, barcodeScanner)
                        }
                        onBarcodeCaptured(bitmap, barcode)
                    }
                    ScanCaptureMode.COVER -> {
                        onCoverCaptured(bitmap)
                        captureProcessingEvents.send(CaptureProcessingEvent.NavigateToLoading)
                    }
                }
            } catch (exception: Exception) {
                onCaptureFailed(
                    exception.message ?: "Could not process the captured image.",
                )
            }
        }
    }

    private fun onBarcodeCaptured(bitmap: Bitmap, upc: String?) {
        if (upc.isNullOrBlank()) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            _uiState.update {
                it.copy(
                    captureErrorMessage = "Could not read the barcode. Hold steady, fill the frame, and try again, or tap Skip.",
                    isProcessingCapture = false,
                )
            }
            return
        }
        scanSessionHolder.recordBarcodeCapture(upc, bitmap)
        _uiState.update {
            it.copy(
                captureMode = ScanCaptureMode.COVER,
                statusMessage = "Capture the cover photo",
                captureErrorMessage = null,
                isProcessingCapture = false,
            )
        }
    }

    private fun onCoverCaptured(bitmap: Bitmap) {
        scanSessionHolder.recordCoverCapture(bitmap)
        _uiState.update {
            it.copy(
                captureErrorMessage = null,
                isProcessingCapture = false,
            )
        }
    }

    fun onCaptureFailed(message: String) {
        _uiState.update {
            it.copy(
                captureErrorMessage = message,
                isProcessingCapture = false,
            )
        }
    }

    fun showManualEntry() {
        if (_uiState.value.captureMode != ScanCaptureMode.COVER) {
            return
        }
        _uiState.update {
            it.copy(
                showManualEntryDialog = true,
                manualEntryError = null,
            )
        }
    }

    fun dismissManualEntry() {
        _uiState.update {
            it.copy(
                showManualEntryDialog = false,
                manualEntryError = null,
            )
        }
    }

    fun submitManualEntry(title: String, year: String): Boolean {
        if (_uiState.value.captureMode != ScanCaptureMode.COVER) {
            return false
        }
        val trimmedTitle = title.trim()
        val trimmedYear = year.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.update { it.copy(manualEntryError = "Title is required.") }
            return false
        }
        if (trimmedYear.isEmpty()) {
            _uiState.update { it.copy(manualEntryError = "Year is required.") }
            return false
        }
        scanSessionHolder.recordManualCoverEntry(trimmedTitle, trimmedYear)
        _uiState.update {
            it.copy(
                showManualEntryDialog = false,
                manualEntryError = null,
            )
        }
        viewModelScope.launch {
            captureProcessingEvents.send(CaptureProcessingEvent.NavigateToLoading)
        }
        return true
    }
}
