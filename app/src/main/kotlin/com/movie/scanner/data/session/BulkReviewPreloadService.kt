package com.movie.scanner.data.session

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.BulkRecognitionProcessor
import com.movie.scanner.util.BulkProcessingResultsJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preloads the next bulk review item while the operator reviews the current one.
 */
@Singleton
class BulkReviewPreloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bulkImageRepository: BulkImageRepository,
    private val bulkRecognitionProcessor: BulkRecognitionProcessor,
    private val bulkQueueSessionState: BulkQueueSessionState,
) {
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val imageLoader = ImageLoader(context)
    private var preloadJob: Job? = null

    @Volatile
    private var preloadedReview: PreloadedBulkReview? = null

    /**
     * Drops any cached next-item payload and cancels an in-flight preload job.
     */
    fun clearPreload() {
        preloadJob?.cancel()
        preloadedReview = null
    }

    /**
     * Starts background preparation for the queue item after [afterRecordId].
     */
    fun schedulePreloadAfter(afterRecordId: Long) {
        preloadJob?.cancel()
        preloadJob = preloadScope.launch {
            val nextRecord = findNextReviewableRecord(afterRecordId = afterRecordId) ?: return@launch
            val payload = buildPreloadedReview(nextRecord) ?: return@launch
            preloadedReview = payload
            preloadCoverImage(payload.coverAbsolutePath)
        }
    }

    /**
     * Returns and clears the cached next-item payload, if preload finished.
     */
    fun takePreloadedReview(): PreloadedBulkReview? {
        val taken = preloadedReview
        preloadedReview = null
        preloadJob?.cancel()
        return taken
    }

    /**
     * Locates the next unprocessed queue row with recognition results, optionally after an id.
     */
    suspend fun findNextReviewableRecord(afterRecordId: Long? = null): BulkUnprocessedImageEntity? {
        val unprocessedRecords = bulkImageRepository.listUnprocessedRecords()
        for (record in unprocessedRecords) {
            if (afterRecordId != null && record.id <= afterRecordId) {
                continue
            }
            if (bulkQueueSessionState.deferredRecordIds.contains(record.id)) {
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
        while (bulkQueueSessionState.shouldContinueProcessing) {
            val record = bulkImageRepository.listUnprocessedRecords()
                .firstOrNull { candidate -> candidate.id == recordId }
                ?: return null
            if (!record.processingResultsJson.isNullOrBlank()) {
                return record
            }
            if (!bulkRecognitionProcessor.recognizingRecordIds.value.contains(recordId)) {
                bulkRecognitionProcessor.scheduleRecognition()
            }
            delay(RECOGNITION_POLL_INTERVAL_MS)
        }
        return null
    }

    private fun buildPreloadedReview(record: BulkUnprocessedImageEntity): PreloadedBulkReview? {
        val processingResultsJson = record.processingResultsJson ?: return null
        if (processingResultsJson.isBlank()) {
            return null
        }
        val results = BulkProcessingResultsJson.parse(processingResultsJson)
        return PreloadedBulkReview(
            recordId = record.id,
            coverRelFilepath = record.coverRelFilepath,
            coverAbsolutePath = bulkImageRepository.resolveAbsolutePath(record.coverRelFilepath),
            coverGuess = results.coverGuess,
            barcodeGuess = results.barcodeGuess,
            tmdbResults = results.tmdbResults,
            capturedUpc = results.capturedUpc,
        )
    }

    private suspend fun preloadCoverImage(absolutePath: String) {
        withContext(Dispatchers.IO) {
            imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(absolutePath)
                    .build(),
            )
        }
    }

    private companion object {
        const val RECOGNITION_POLL_INTERVAL_MS = 250L
    }
}
