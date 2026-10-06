package com.movie.scanner.data.session

import android.content.Context
import com.movie.scanner.data.model.BulkProcessingResults
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.BulkRecognitionProcessor
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.util.BulkProcessingResultsJson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BulkReviewPreloadServiceTest {
    private val context = mockk<Context>(relaxed = true)
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val bulkRecognitionProcessor = mockk<BulkRecognitionProcessor>(relaxed = true)
    private val bulkQueueSessionState = BulkQueueSessionState()
    private val movieRepository = mockk<MovieRepository>(relaxed = true)

    private fun createService(): BulkReviewPreloadService =
        BulkReviewPreloadService(
            context = context,
            bulkImageRepository = bulkImageRepository,
            bulkRecognitionProcessor = bulkRecognitionProcessor,
            bulkQueueSessionState = bulkQueueSessionState,
            movieRepository = movieRepository,
        )

    @Test
    fun findNextReviewableRecord_skipsRowWhenBarcodeAlreadyOnList() = runTest {
        val duplicateUpc = "9781234567890"
        val duplicateResultsJson = BulkProcessingResultsJson.encode(
            BulkProcessingResults(capturedUpc = duplicateUpc),
        )
        val duplicateRecord = BulkUnprocessedImageEntity(
            id = 1L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = "barcode_1.jpg",
            coverRelFilepath = "cover_1.jpg",
            processingResultsJson = duplicateResultsJson,
        )
        val nextResultsJson = BulkProcessingResultsJson.encode(
            BulkProcessingResults(capturedUpc = "1112223334445"),
        )
        val nextRecord = BulkUnprocessedImageEntity(
            id = 2L,
            createdAtTimestamp = 200L,
            barcodeRelFilepath = "barcode_2.jpg",
            coverRelFilepath = "cover_2.jpg",
            processingResultsJson = nextResultsJson,
        )
        coEvery { bulkImageRepository.listUnprocessedRecords() } returns listOf(duplicateRecord, nextRecord)
        coEvery { movieRepository.listNormalizedUpcsInList() } returns setOf(duplicateUpc)
        every { bulkRecognitionProcessor.recognizingRecordIds } returns MutableStateFlow(emptySet())
        bulkQueueSessionState.shouldContinueProcessing = true

        val service = createService()
        val resolved = service.findNextReviewableRecord(afterRecordId = null)

        assertEquals(nextRecord, resolved)
    }

    @Test
    fun findNextReviewableRecord_returnsNullWhenOnlyDuplicatesRemain() = runTest {
        val duplicateUpc = "9781234567890"
        val duplicateResultsJson = BulkProcessingResultsJson.encode(
            BulkProcessingResults(capturedUpc = duplicateUpc),
        )
        val duplicateRecord = BulkUnprocessedImageEntity(
            id = 1L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = "barcode_1.jpg",
            coverRelFilepath = "cover_1.jpg",
            processingResultsJson = duplicateResultsJson,
        )
        coEvery { bulkImageRepository.listUnprocessedRecords() } returns listOf(duplicateRecord)
        coEvery { movieRepository.listNormalizedUpcsInList() } returns setOf(duplicateUpc)
        every { bulkRecognitionProcessor.recognizingRecordIds } returns MutableStateFlow(emptySet())
        bulkQueueSessionState.shouldContinueProcessing = true

        val service = createService()
        val resolved = service.findNextReviewableRecord(afterRecordId = null)

        assertNull(resolved)
    }
}
