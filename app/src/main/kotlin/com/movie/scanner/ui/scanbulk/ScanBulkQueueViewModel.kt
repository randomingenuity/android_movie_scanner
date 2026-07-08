package com.movie.scanner.ui.scanbulk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.BulkRecognitionProcessor
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.BulkProcessingResultsJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

sealed interface ScanBulkQueueEvent {
    data object NavigateToReview : ScanBulkQueueEvent

    data object NavigateToScan : ScanBulkQueueEvent
}

enum class BulkQueueItemStatus {
    PENDING,
    RECOGNIZING,
    READY,
    PROCESSED,
}

data class ScanBulkQueueRow(
    val id: Long,
    val timestampLabel: String,
    val barcodeRelFilepath: String,
    val coverRelFilepath: String,
    val status: BulkQueueItemStatus,
)

data class ScanBulkQueueUiState(
    val records: List<ScanBulkQueueRow> = emptyList(),
    val isProcessing: Boolean = false,
    val processingRecordId: Long? = null,
    val previewImagePath: String? = null,
)

@HiltViewModel
class ScanBulkQueueViewModel @Inject constructor(
    private val bulkImageRepository: BulkImageRepository,
    private val bulkRecognitionProcessor: BulkRecognitionProcessor,
    private val scanSessionHolder: ScanSessionHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanBulkQueueUiState())
    val uiState: StateFlow<ScanBulkQueueUiState> = _uiState.asStateFlow()
    private val navigationEvents = Channel<ScanBulkQueueEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    private val timestampFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private var shouldContinueProcessing = false
    private val deferredRecordIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            combine(
                bulkImageRepository.observeAllRecords(),
                bulkRecognitionProcessor.recognizingRecordIds,
            ) { records, recognizingRecordIds ->
                records.map { record ->
                    buildQueueRow(
                        record = record,
                        recognizingRecordIds = recognizingRecordIds,
                    )
                }
            }.collect { rows ->
                _uiState.update { state ->
                    state.copy(records = rows)
                }
            }
        }
    }

    fun startProcessing() {
        if (_uiState.value.isProcessing) {
            return
        }
        deferredRecordIds.clear()
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
        deferredRecordIds.clear()
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingRecordId = null,
            )
        }
        viewModelScope.launch {
            navigationEvents.send(ScanBulkQueueEvent.NavigateToScan)
        }
    }

    /**
     * Clears active queue processing before opening bulk capture to add more pairs.
     */
    fun prepareForBulkCapture() {
        shouldContinueProcessing = false
        deferredRecordIds.clear()
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingRecordId = null,
            )
        }
    }

    fun resumeProcessingIfNeeded() {
        if (scanSessionHolder.consumeBulkProcessingStopRequested()) {
            shouldContinueProcessing = false
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
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
        _uiState.value.processingRecordId?.let { recordId ->
            deferredRecordIds.add(recordId)
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
                )
            }
            return
        }
        val nextRecord = findNextReviewableRecord()
        if (nextRecord == null) {
            shouldContinueProcessing = false
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isProcessing = true,
                processingRecordId = nextRecord.id,
            )
        }
        val processingResultsJson = nextRecord.processingResultsJson
        if (processingResultsJson.isNullOrBlank()) {
            processNextRecord()
            return
        }
        val results = BulkProcessingResultsJson.parse(processingResultsJson)
        scanSessionHolder.startNewScan()
        scanSessionHolder.startBulkItem(
            recordId = nextRecord.id,
            coverRelFilepath = nextRecord.coverRelFilepath,
        )
        scanSessionHolder.storeRecognitionResults(
            coverGuessValue = results.coverGuess,
            barcodeGuessValue = results.barcodeGuess,
            tmdbResults = results.tmdbResults,
            capturedUpcValue = results.capturedUpc,
        )
        navigationEvents.send(ScanBulkQueueEvent.NavigateToReview)
    }

    private suspend fun findNextReviewableRecord(): BulkUnprocessedImageEntity? {
        val unprocessedRecords = bulkImageRepository.listUnprocessedRecords()
        for (record in unprocessedRecords) {
            if (deferredRecordIds.contains(record.id)) {
                continue
            }
            if (!record.processingResultsJson.isNullOrBlank()) {
                return record
            }
            if (bulkRecognitionProcessor.recognizingRecordIds.value.contains(record.id)) {
                return waitForRecognition(record.id)
            }
            bulkRecognitionProcessor.scheduleRecognition()
            return waitForRecognition(record.id)
        }
        return null
    }

    private suspend fun waitForRecognition(recordId: Long): BulkUnprocessedImageEntity? {
        while (shouldContinueProcessing) {
            val record = bulkImageRepository.listUnprocessedRecords()
                .firstOrNull { candidate -> candidate.id == recordId }
                ?: return null
            if (!record.processingResultsJson.isNullOrBlank()) {
                return record
            }
            if (!bulkRecognitionProcessor.recognizingRecordIds.value.contains(recordId)) {
                bulkRecognitionProcessor.scheduleRecognition()
            }
            kotlinx.coroutines.delay(250)
        }
        return null
    }

    private fun buildQueueRow(
        record: BulkUnprocessedImageEntity,
        recognizingRecordIds: Set<Long>,
    ): ScanBulkQueueRow =
        ScanBulkQueueRow(
            id = record.id,
            timestampLabel = timestampFormatter.format(Date(record.createdAtTimestamp)),
            barcodeRelFilepath = record.barcodeRelFilepath,
            coverRelFilepath = record.coverRelFilepath,
            status = resolveItemStatus(
                record = record,
                recognizingRecordIds = recognizingRecordIds,
            ),
        )

    private fun resolveItemStatus(
        record: BulkUnprocessedImageEntity,
        recognizingRecordIds: Set<Long>,
    ): BulkQueueItemStatus {
        if (record.wasProcessed) {
            return BulkQueueItemStatus.PROCESSED
        }
        if (recognizingRecordIds.contains(record.id)) {
            return BulkQueueItemStatus.RECOGNIZING
        }
        if (!record.processingResultsJson.isNullOrBlank()) {
            return BulkQueueItemStatus.READY
        }
        return BulkQueueItemStatus.PENDING
    }
}
