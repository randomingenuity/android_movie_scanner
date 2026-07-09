package com.movie.scanner.ui.scanbulk

import com.movie.scanner.data.model.BulkProcessingResults
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.BulkRecognitionProcessor
import com.movie.scanner.data.session.BulkQueueSessionState
import com.movie.scanner.data.session.BulkReviewPreloadService
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.BulkProcessingResultsJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanBulkQueueViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val bulkRecognitionProcessor = mockk<BulkRecognitionProcessor>(relaxed = true)
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)
    private val bulkQueueSessionState = BulkQueueSessionState()
    private val bulkReviewPreloadService = mockk<BulkReviewPreloadService>(relaxed = true)
    private val readyResultsJson = BulkProcessingResultsJson.encode(BulkProcessingResults())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { bulkImageRepository.observeAllRecords() } returns flowOf(emptyList())
        every { bulkRecognitionProcessor.recognizingRecordIds } returns MutableStateFlow(emptySet())
        every { scanSessionHolder.consumeBulkProcessingStopRequested() } returns false
        every { scanSessionHolder.consumeBulkQueueResume() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_clearsLoadingAfterFirstRecordsEmission() = runTest {
        val viewModel = ScanBulkQueueViewModel(
            bulkImageRepository = bulkImageRepository,
            bulkRecognitionProcessor = bulkRecognitionProcessor,
            scanSessionHolder = scanSessionHolder,
            bulkQueueSessionState = bulkQueueSessionState,
            bulkReviewPreloadService = bulkReviewPreloadService,
        )

        assertTrue(viewModel.uiState.value.isLoadingRecords)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingRecords)
        assertTrue(viewModel.uiState.value.records.isEmpty())
    }

    @Test
    fun init_mapsBarcodeOnlyRecognitionToQueueRowFlag() = runTest {
        val barcodeOnlyResultsJson = BulkProcessingResultsJson.encode(
            BulkProcessingResults(
                barcodeGuess = MovieGuess(title = "Arrival", year = "2016"),
                tmdbResults = listOf(
                    TmdbSearchResult(
                        id = 42,
                        title = "Arrival",
                        year = "2016",
                        posterUrl = null,
                        tmdbUrl = "https://www.themoviedb.org/movie/42",
                    ),
                ),
                capturedUpc = "9781234567890",
            ),
        )
        val record = BulkUnprocessedImageEntity(
            id = 1L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = "barcode_1.jpg",
            coverRelFilepath = "cover_1.jpg",
            processingResultsJson = barcodeOnlyResultsJson,
        )
        every { bulkImageRepository.observeAllRecords() } returns flowOf(listOf(record))

        val viewModel = ScanBulkQueueViewModel(
            bulkImageRepository = bulkImageRepository,
            bulkRecognitionProcessor = bulkRecognitionProcessor,
            scanSessionHolder = scanSessionHolder,
            bulkQueueSessionState = bulkQueueSessionState,
            bulkReviewPreloadService = bulkReviewPreloadService,
        )
        advanceUntilIdle()

        val row = viewModel.uiState.value.records.single()
        assertTrue(row.showBarcodeResultIcon)
        assertEquals(BulkQueueItemStatus.READY, row.status)
    }

    @Test
    fun resumeProcessingIfNeeded_afterSkip_opensNextQueueItem() = runTest {
        val firstRecord = BulkUnprocessedImageEntity(
            id = 1L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = "barcode_1.jpg",
            coverRelFilepath = "cover_1.jpg",
            processingResultsJson = readyResultsJson,
        )
        val secondRecord = BulkUnprocessedImageEntity(
            id = 2L,
            createdAtTimestamp = 200L,
            barcodeRelFilepath = "barcode_2.jpg",
            coverRelFilepath = "cover_2.jpg",
            processingResultsJson = readyResultsJson,
        )
        coEvery { bulkImageRepository.listUnprocessedRecords() } returns listOf(firstRecord, secondRecord)
        coEvery { bulkReviewPreloadService.findNextReviewableRecord(afterRecordId = null) } returns firstRecord
        coEvery { bulkReviewPreloadService.findNextReviewableRecord(afterRecordId = 1L) } returns secondRecord
        every { bulkReviewPreloadService.takePreloadedReview() } returns null

        val viewModel = ScanBulkQueueViewModel(
            bulkImageRepository = bulkImageRepository,
            bulkRecognitionProcessor = bulkRecognitionProcessor,
            scanSessionHolder = scanSessionHolder,
            bulkQueueSessionState = bulkQueueSessionState,
            bulkReviewPreloadService = bulkReviewPreloadService,
        )
        val navigationEvents = mutableListOf<ScanBulkQueueEvent>()
        val collectorJob = launch {
            viewModel.navigationEventFlow.collect { event ->
                navigationEvents.add(event)
            }
        }
        advanceUntilIdle()

        viewModel.startProcessing()
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.processingRecordId)
        assertEquals(listOf(ScanBulkQueueEvent.NavigateToReview), navigationEvents)
        verify {
            scanSessionHolder.startBulkItem(
                recordId = 1L,
                coverRelFilepath = "cover_1.jpg",
            )
        }
        io.mockk.verify { bulkReviewPreloadService.schedulePreloadAfter(1L) }

        every { scanSessionHolder.consumeBulkQueueResume() } returns true
        viewModel.resumeProcessingIfNeeded()
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.processingRecordId)
        assertEquals(
            listOf(
                ScanBulkQueueEvent.NavigateToReview,
                ScanBulkQueueEvent.NavigateToReview,
            ),
            navigationEvents,
        )
        verify {
            scanSessionHolder.startBulkItem(
                recordId = 2L,
                coverRelFilepath = "cover_2.jpg",
            )
        }
        collectorJob.cancel()
    }

    @Test
    fun deleteRecord_deletesQueueRowThroughRepository() = runTest {
        val viewModel = ScanBulkQueueViewModel(
            bulkImageRepository = bulkImageRepository,
            bulkRecognitionProcessor = bulkRecognitionProcessor,
            scanSessionHolder = scanSessionHolder,
            bulkQueueSessionState = bulkQueueSessionState,
            bulkReviewPreloadService = bulkReviewPreloadService,
        )
        advanceUntilIdle()

        viewModel.deleteRecord(9L)
        advanceUntilIdle()

        coVerify { bulkImageRepository.deleteRecord(9L) }
    }

    @Test
    fun clearDoneRecords_deletesProcessedRowsThroughRepository() = runTest {
        val viewModel = ScanBulkQueueViewModel(
            bulkImageRepository = bulkImageRepository,
            bulkRecognitionProcessor = bulkRecognitionProcessor,
            scanSessionHolder = scanSessionHolder,
            bulkQueueSessionState = bulkQueueSessionState,
            bulkReviewPreloadService = bulkReviewPreloadService,
        )
        advanceUntilIdle()

        viewModel.clearDoneRecords()
        advanceUntilIdle()

        coVerify { bulkImageRepository.deleteProcessedRecords() }
    }
}
