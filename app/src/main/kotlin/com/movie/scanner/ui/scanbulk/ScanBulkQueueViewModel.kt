package com.movie.scanner.ui.scanbulk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.BulkRecognitionProcessor
import com.movie.scanner.data.session.BulkQueueSessionState
import com.movie.scanner.data.session.BulkReviewPreloadService
import com.movie.scanner.data.session.PreloadedBulkReview
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
    val isLoadingRecords: Boolean = true,
    val isProcessing: Boolean = false,
    val processingRecordId: Long? = null,
    val previewImagePath: String? = null,
)

@HiltViewModel
class ScanBulkQueueViewModel @Inject constructor(
    private val bulkImageRepository: BulkImageRepository,
    private val bulkRecognitionProcessor: BulkRecognitionProcessor,
    private val scanSessionHolder: ScanSessionHolder,
    private val bulkQueueSessionState: BulkQueueSessionState,
    private val bulkReviewPreloadService: BulkReviewPreloadService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanBulkQueueUiState())
    val uiState: StateFlow<ScanBulkQueueUiState> = _uiState.asStateFlow()
    private val navigationEvents = Channel<ScanBulkQueueEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    private val timestampFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

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
                    state.copy(
                        records = rows,
                        isLoadingRecords = false,
                    )
                }
            }
        }
    }

    fun startProcessing() {
        if (_uiState.value.isProcessing) {
            return
        }
        bulkReviewPreloadService.clearPreload()
        bulkQueueSessionState.resetForNewProcessingRun()
        bulkQueueSessionState.shouldContinueProcessing = true
        viewModelScope.launch {
            processNextRecord()
        }
    }

    /**
     * Stops queue processing and navigates back to the main Scan tab.
     */
    fun exitToScan() {
        stopActiveProcessing()
        viewModelScope.launch {
            navigationEvents.send(ScanBulkQueueEvent.NavigateToScan)
        }
    }

    /**
     * Clears active queue processing before opening bulk capture to add more pairs.
     */
    fun prepareForBulkCapture() {
        stopActiveProcessing()
    }

    fun resumeProcessingIfNeeded() {
        if (scanSessionHolder.consumeBulkProcessingStopRequested()) {
            stopActiveProcessing()
            return
        }
        if (!scanSessionHolder.consumeBulkQueueResume()) {
            return
        }
        if (!bulkQueueSessionState.shouldContinueProcessing || !_uiState.value.isProcessing) {
            return
        }
        bulkQueueSessionState.processingRecordId?.let { recordId ->
            bulkQueueSessionState.deferRecord(recordId)
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
                stopActiveProcessing()
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

    private fun stopActiveProcessing() {
        bulkQueueSessionState.shouldContinueProcessing = false
        bulkReviewPreloadService.clearPreload()
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingRecordId = null,
            )
        }
    }

    private suspend fun processNextRecord() {
        if (!bulkQueueSessionState.shouldContinueProcessing) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
                )
            }
            return
        }
        val preloadedReview = bulkReviewPreloadService.takePreloadedReview()
        val nextRecord = resolveNextRecord(preloadedReview)
        if (nextRecord == null) {
            bulkQueueSessionState.shouldContinueProcessing = false
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingRecordId = null,
                )
            }
            return
        }
        bulkQueueSessionState.processingRecordId = nextRecord.id
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
        applyRecordToSession(
            record = nextRecord,
            preloadedReview = preloadedReview?.takeIf { cached -> cached.recordId == nextRecord.id },
        )
        bulkReviewPreloadService.schedulePreloadAfter(nextRecord.id)
        navigationEvents.send(ScanBulkQueueEvent.NavigateToReview)
    }

    private suspend fun resolveNextRecord(
        preloadedReview: PreloadedBulkReview?,
    ): BulkUnprocessedImageEntity? {
        if (preloadedReview != null) {
            val cachedRecord = bulkImageRepository.listUnprocessedRecords()
                .firstOrNull { record -> record.id == preloadedReview.recordId }
            if (cachedRecord != null && !cachedRecord.processingResultsJson.isNullOrBlank()) {
                return cachedRecord
            }
        }
        return bulkReviewPreloadService.findNextReviewableRecord(
            afterRecordId = bulkQueueSessionState.processingRecordId,
        )
    }

    private fun applyRecordToSession(
        record: BulkUnprocessedImageEntity,
        preloadedReview: PreloadedBulkReview?,
    ) {
        scanSessionHolder.startNewScan()
        scanSessionHolder.startBulkItem(
            recordId = record.id,
            coverRelFilepath = record.coverRelFilepath,
        )
        if (preloadedReview != null) {
            scanSessionHolder.storeRecognitionResults(
                coverGuessValue = preloadedReview.coverGuess,
                barcodeGuessValue = preloadedReview.barcodeGuess,
                tmdbResults = preloadedReview.tmdbResults,
                capturedUpcValue = preloadedReview.capturedUpc,
            )
            return
        }
        val results = BulkProcessingResultsJson.parse(record.processingResultsJson.orEmpty())
        scanSessionHolder.storeRecognitionResults(
            coverGuessValue = results.coverGuess,
            barcodeGuessValue = results.barcodeGuess,
            tmdbResults = results.tmdbResults,
            capturedUpcValue = results.capturedUpc,
        )
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
