package com.movie.scanner.ui.scanbulk

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.data.repository.BulkImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanBulkCaptureEvent {
    data object NavigateToQueue : ScanBulkCaptureEvent
}

data class ScanBulkCaptureUiState(
    val captureMode: ScanCaptureMode = ScanCaptureMode.BARCODE,
    val isConfigured: Boolean = false,
    val isProcessingCapture: Boolean = false,
    val pairCount: Int = 0,
    val currentPairNumber: Int = 1,
    val statusMessage: String = "Take a photo of the barcode",
    val captureErrorMessage: String? = null,
)

@HiltViewModel
class ScanBulkCaptureViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val bulkImageRepository: BulkImageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScanBulkCaptureUiState(isConfigured = apiKeyStore.hasMinimumConfiguration()),
    )
    val uiState: StateFlow<ScanBulkCaptureUiState> = _uiState.asStateFlow()
    private val navigationEvents = Channel<ScanBulkCaptureEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    private var pendingBarcodeBitmap: Bitmap? = null

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
        _uiState.value = ScanBulkCaptureUiState(
            isConfigured = apiKeyStore.hasMinimumConfiguration(),
            pairCount = _uiState.value.pairCount,
            currentPairNumber = _uiState.value.pairCount + 1,
        )
    }

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

    fun processCapturedImage(bitmap: Bitmap) {
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
        val barcodeBitmap = pendingBarcodeBitmap
        if (barcodeBitmap == null) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            _uiState.update {
                it.copy(
                    captureErrorMessage = "Barcode photo is missing. Capture the barcode again.",
                    captureMode = ScanCaptureMode.BARCODE,
                    statusMessage = "Take a photo of the barcode",
                    isProcessingCapture = false,
                )
            }
            return
        }
        pendingBarcodeBitmap = null
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
                isProcessingCapture = false,
            )
        }
    }

    fun finishScanning() {
        viewModelScope.launch {
            navigationEvents.send(ScanBulkCaptureEvent.NavigateToQueue)
        }
    }
}
