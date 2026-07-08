package com.movie.scanner.ui.scanbulk

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
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
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

sealed interface ScanBulkQueueEvent {
    data object NavigateToLoading : ScanBulkQueueEvent

    data object NavigateToScan : ScanBulkQueueEvent
}

data class ScanBulkQueueRow(
    val id: Long,
    val timestampLabel: String,
    val barcodeRelFilepath: String,
    val coverRelFilepath: String,
    val wasProcessed: Boolean,
)

data class ScanBulkQueueUiState(
    val records: List<ScanBulkQueueRow> = emptyList(),
    val isProcessing: Boolean = false,
    val processingRecordId: Long? = null,
    val processingRecordNumber: Int? = null,
    val previewImagePath: String? = null,
)

@HiltViewModel
class ScanBulkQueueViewModel @Inject constructor(
    private val bulkImageRepository: BulkImageRepository,
    private val scanSessionHolder: ScanSessionHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanBulkQueueUiState())
    val uiState: StateFlow<ScanBulkQueueUiState> = _uiState.asStateFlow()
    private val navigationEvents = Channel<ScanBulkQueueEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    private val barcodeScanner: BarcodeScanner = BarcodeDecoder.buildBarcodeScanner()
    private val timestampFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private var shouldContinueProcessing = false

    init {
        viewModelScope.launch {
            bulkImageRepository.observeAllRecords().collect { records ->
                _uiState.update { state ->
                    state.copy(records = records.map(::buildQueueRow))
                }
            }
        }
    }

    override fun onCleared() {
        barcodeScanner.close()
        super.onCleared()
    }

    fun startProcessing() {
        if (_uiState.value.isProcessing) {
            return
        }
        shouldContinueProcessing = true
        viewModelScope.launch {
            processNextRecord()
        }
    }

    /**
     * Stops queue processing and navigates back to the main Scan tab.
     */
    fun exitToScan() {
        shouldContinueProcessing = false
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingRecordId = null,
                processingRecordNumber = null,
            )
        }
        viewModelScope.launch {
            navigationEvents.send(ScanBulkQueueEvent.NavigateToScan)
        }
    }

    fun resumeProcessingIfNeeded() {
        if (scanSessionHolder.consumeBulkProcessingStopRequested()) {
            shouldContinueProcessing = false
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
                    processingRecordNumber = null,
                )
            }
            return
        }
        if (!scanSessionHolder.consumeBulkQueueResume()) {
            return
        }
        if (!shouldContinueProcessing || !_uiState.value.isProcessing) {
            return
        }
        viewModelScope.launch {
            processNextRecord()
        }
    }

    fun showImagePreview(relativeFilepath: String) {
        _uiState.update { it.copy(previewImagePath = relativeFilepath) }
    }

    fun dismissImagePreview() {
        _uiState.update { it.copy(previewImagePath = null) }
    }

    fun resolveAbsolutePath(relativeFilepath: String): String =
        bulkImageRepository.resolveAbsolutePath(relativeFilepath)

    /**
     * Deletes a queue row from storage and clears processing state when that row was active.
     */
    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            if (_uiState.value.processingRecordId == recordId) {
                shouldContinueProcessing = false
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingRecordId = null,
                        processingRecordNumber = null,
                    )
                }
            }
            bulkImageRepository.deleteRecord(recordId)
        }
    }

    /**
     * Deletes all processed queue rows and their image files from disk.
     */
    fun clearDoneRecords() {
        if (_uiState.value.isProcessing) {
            return
        }
        viewModelScope.launch {
            bulkImageRepository.deleteProcessedRecords()
        }
    }

    private suspend fun processNextRecord() {
        if (!shouldContinueProcessing) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
                    processingRecordNumber = null,
                )
            }
            return
        }
        val nextRecord = bulkImageRepository.listUnprocessedRecords().firstOrNull()
        if (nextRecord == null) {
            shouldContinueProcessing = false
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
                    processingRecordNumber = null,
                )
            }
            return
        }
        val recordNumber = _uiState.value.records.indexOfFirst { row -> row.id == nextRecord.id } + 1
        _uiState.update {
            it.copy(
                isProcessing = true,
                processingRecordId = nextRecord.id,
                processingRecordNumber = recordNumber,
            )
        }
        val barcodeBitmap = bulkImageRepository.loadBitmap(nextRecord.barcodeRelFilepath)
        val coverBitmap = bulkImageRepository.loadBitmap(nextRecord.coverRelFilepath)
        if (barcodeBitmap == null || coverBitmap == null) {
            bulkImageRepository.markProcessed(nextRecord.id)
            processNextRecord()
            return
        }
        prepareBulkSession(
            record = nextRecord,
            barcodeBitmap = barcodeBitmap,
            coverBitmap = coverBitmap,
        )
        navigationEvents.send(ScanBulkQueueEvent.NavigateToLoading)
    }

    private suspend fun prepareBulkSession(
        record: BulkUnprocessedImageEntity,
        barcodeBitmap: Bitmap,
        coverBitmap: Bitmap,
    ) {
        scanSessionHolder.startNewScan()
        scanSessionHolder.startBulkItem(
            recordId = record.id,
            coverRelFilepath = record.coverRelFilepath,
        )
        val decodedBarcode = withContext(Dispatchers.Default) {
            BarcodeDecoder.decodeFromBitmap(barcodeBitmap, barcodeScanner)
        }
        if (!decodedBarcode.isNullOrBlank()) {
            scanSessionHolder.recordBarcodeCapture(decodedBarcode, barcodeBitmap)
        } else {
            scanSessionHolder.recordBarcodeCaptureForBulk(barcodeBitmap)
        }
        scanSessionHolder.recordCoverCapture(coverBitmap)
    }

    private fun buildQueueRow(record: BulkUnprocessedImageEntity): ScanBulkQueueRow =
        ScanBulkQueueRow(
            id = record.id,
            timestampLabel = timestampFormatter.format(Date(record.createdAtTimestamp)),
            barcodeRelFilepath = record.barcodeRelFilepath,
            coverRelFilepath = record.coverRelFilepath,
            wasProcessed = record.wasProcessed,
        )
}
