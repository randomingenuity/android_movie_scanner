package com.movie.scanner.ui.review

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.data.repository.TmdbRepository
import com.movie.scanner.data.session.BulkQueueSessionState
import com.movie.scanner.data.session.BulkReviewPreloadService
import com.movie.scanner.data.session.PreloadedBulkReview
import com.movie.scanner.data.session.ScanSessionHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)
    private val tmdbRepository = mockk<TmdbRepository>(relaxed = true)
    private val movieRepository = mockk<MovieRepository>(relaxed = true)
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val bulkQueueSessionState = BulkQueueSessionState()
    private val bulkReviewPreloadService = mockk<BulkReviewPreloadService>(relaxed = true)

    private fun createViewModel(): ReviewViewModel =
        ReviewViewModel(
            scanSessionHolder = scanSessionHolder,
            tmdbRepository = tmdbRepository,
            movieRepository = movieRepository,
            bulkImageRepository = bulkImageRepository,
            bulkQueueSessionState = bulkQueueSessionState,
            bulkReviewPreloadService = bulkReviewPreloadService,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bulkQueueSessionState.resetForNewProcessingRun()
        every { scanSessionHolder.coverGuess } returns MovieGuess(title = "Cover Title", year = "2020")
        every { scanSessionHolder.barcodeGuess } returns MovieGuess(title = "Barcode Title", year = "2019")
        every { scanSessionHolder.initialTmdbResults } returns listOf(
            TmdbSearchResult(
                id = 1,
                title = "Cover Title",
                year = "2020",
                posterUrl = null,
                tmdbUrl = "https://www.themoviedb.org/movie/1",
            ),
        )
        every { scanSessionHolder.resolveCapturedUpc() } returns "9781234567890"
        every { scanSessionHolder.lastReviewFeatureType } returns FeatureType.MOVIE
        every { scanSessionHolder.lastReviewLocation } returns ""
        every { scanSessionHolder.bulkBatchLocation } returns ""
        every { scanSessionHolder.isBulkProcessing } returns false
        every { scanSessionHolder.bulkCoverRelFilepath } returns null
        coEvery { movieRepository.existsByTmdbId(any()) } returns false
        coEvery { movieRepository.existsByTitleAndYear(any(), any()) } returns false
        coEvery { movieRepository.existsByTitleAndSeason(any(), any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_prefillsTitleAndYearFromCoverGuess() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Cover Title", viewModel.uiState.value.title)
        assertEquals("2020", viewModel.uiState.value.year)
        assertEquals("9781234567890", viewModel.uiState.value.barcode)
    }

    @Test
    fun init_fallsBackToBarcodeGuessWhenCoverMissing() = runTest {
        every { scanSessionHolder.coverGuess } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Barcode Title", viewModel.uiState.value.title)
        assertEquals("2019", viewModel.uiState.value.year)
        assertTrue(viewModel.uiState.value.barcodeUsedForTitle)
    }

    @Test
    fun init_doesNotMarkBarcodeUsedForTitleWhenCoverProvidesTitle() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Cover Title", viewModel.uiState.value.extractedCoverTitle)
        assertEquals(false, viewModel.uiState.value.barcodeUsedForTitle)
    }

    @Test
    fun updateBarcode_stripsNewlines() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val sanitized = viewModel.updateBarcode("9781234567890\r\n")
        viewModel.commitBarcode(sanitized)

        assertEquals("9781234567890", sanitized)
        assertEquals("9781234567890", viewModel.uiState.value.barcode)
    }

    @Test
    fun init_restoresLocationFromPreviousEntry() = runTest {
        every { scanSessionHolder.lastReviewLocation } returns "Shelf A"

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Shelf A", viewModel.uiState.value.location)
    }

    @Test
    fun init_prefillsBulkBatchLocationDuringBulkProcessing() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.bulkBatchLocation } returns "Shelf A"
        every { scanSessionHolder.lastReviewLocation } returns ""

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Shelf A", viewModel.uiState.value.location)
    }

    @Test
    fun refreshActionState_prefillsBulkBatchLocationWhenDuplicateHasNoLocation() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.bulkBatchLocation } returns "Shelf A"
        val existingMovie = MovieEntity(
            id = 9L,
            title = "Cover Title",
            year = "2020",
            tmdbId = 1,
            tmdbUrl = "https://www.themoviedb.org/movie/1",
            posterUrl = null,
            upc = "111111111111",
            isForceAdded = false,
            sortOrder = 0,
            featureType = FeatureType.MOVIE.label,
            discType = "Blu-Ray",
            location = null,
        )
        coEvery { movieRepository.existsByTmdbId(1) } returns true
        coEvery { movieRepository.findByTmdbId(1) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Shelf A", viewModel.uiState.value.location)
        assertEquals("Blu-Ray", viewModel.uiState.value.discType)
    }

    @Test
    fun updateLocation_remembersClearedValue() = runTest {
        every { scanSessionHolder.lastReviewLocation } returns "Shelf A"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateLocation("")

        io.mockk.verify { scanSessionHolder.rememberReviewLocation("") }
        assertEquals("", viewModel.uiState.value.location)
    }

    @Test
    fun init_restoresFeatureTypeFromPreviousEntry() = runTest {
        every { scanSessionHolder.lastReviewFeatureType } returns FeatureType.TV

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(FeatureType.TV, viewModel.uiState.value.featureType)
    }

    @Test
    fun updateFeatureType_remembersSelectionForNextEntry() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateFeatureType(FeatureType.TV)

        io.mockk.verify { scanSessionHolder.rememberReviewFeatureType(FeatureType.TV) }
    }

    @Test
    fun refreshActionState_loadsExistingEntryMetadataWhenDuplicateFound() = runTest {
        every { scanSessionHolder.lastReviewFeatureType } returns FeatureType.TV
        val existingMovie = MovieEntity(
            id = 9L,
            title = "Cover Title",
            year = "2020",
            tmdbId = 1,
            tmdbUrl = "https://www.themoviedb.org/movie/1",
            posterUrl = null,
            upc = "111111111111",
            isForceAdded = false,
            sortOrder = 0,
            featureType = FeatureType.TV.label,
            discType = "DVD",
            location = "Shelf B",
            seasonNumber = 2,
            numberOfDiscs = 4,
        )
        coEvery { movieRepository.existsByTitleAndSeason("Cover Title", 2) } returns true
        coEvery { movieRepository.findByTitleAndSeason("Cover Title", 2) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSeasonNumberInput("2")
        advanceUntilIdle()

        assertEquals(FeatureType.TV, viewModel.uiState.value.featureType)
        assertEquals("DVD", viewModel.uiState.value.discType)
        assertEquals("Shelf B", viewModel.uiState.value.location)
        assertEquals("2", viewModel.uiState.value.seasonNumberInput)
        assertEquals("4", viewModel.uiState.value.numberOfDiscsInput)
        assertEquals("9781234567890", viewModel.uiState.value.barcode)
    }

    @Test
    fun refreshActionState_usesStoredBarcodeWhenScanDidNotCaptureOne() = runTest {
        every { scanSessionHolder.resolveCapturedUpc() } returns null
        val existingMovie = MovieEntity(
            id = 9L,
            title = "Cover Title",
            year = "2020",
            tmdbId = 1,
            tmdbUrl = "https://www.themoviedb.org/movie/1",
            posterUrl = null,
            upc = "111111111111",
            isForceAdded = false,
            sortOrder = 0,
            featureType = FeatureType.MOVIE.label,
            discType = "Blu-Ray",
            location = "Shelf A",
        )
        coEvery { movieRepository.existsByTmdbId(1) } returns true
        coEvery { movieRepository.findByTmdbId(1) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("111111111111", viewModel.uiState.value.barcode)
        assertEquals("Blu-Ray", viewModel.uiState.value.discType)
        assertEquals("Shelf A", viewModel.uiState.value.location)
    }

    @Test
    fun refreshActionState_showsReplaceWhenMovieAlreadyExists() = runTest {
        coEvery { movieRepository.existsByTmdbId(1) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.showReplaceAdd)
        assertEquals(
            "Already in list. Replace will replace the existing entry.",
            viewModel.actionState.value.duplicateMessage,
        )
    }

    @Test
    fun refreshActionState_doesNotMatchTelevisionDuplicateUntilSeasonEntered() = runTest {
        every { scanSessionHolder.lastReviewFeatureType } returns FeatureType.TV
        coEvery { movieRepository.existsByTmdbId(1) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.actionState.value.showReplaceAdd)
        assertEquals(null, viewModel.actionState.value.duplicateMessage)
        assertEquals(false, viewModel.actionState.value.isAddEnabled)
    }

    @Test
    fun refreshActionState_showsReplaceWhenTelevisionTitleAndSeasonAlreadyExist() = runTest {
        every { scanSessionHolder.lastReviewFeatureType } returns FeatureType.TV
        coEvery { movieRepository.existsByTitleAndSeason("Cover Title", 2) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSeasonNumberInput("2")
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.showReplaceAdd)
        assertEquals(true, viewModel.actionState.value.isAddEnabled)
        assertEquals(
            "Already in list. Replace will replace the existing entry.",
            viewModel.actionState.value.duplicateMessage,
        )
    }

    @Test
    fun refreshActionState_showsForceReplaceWhenTitleAndYearAlreadyExist() = runTest {
        every { scanSessionHolder.initialTmdbResults } returns emptyList()
        coEvery { movieRepository.existsByTitleAndYear("Cover Title", "2020") } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.showForceAdd)
        assertEquals(true, viewModel.actionState.value.showForceReplace)
        assertEquals(
            "Already in list. Force Replace will replace the existing entry.",
            viewModel.actionState.value.duplicateMessage,
        )
    }

    @Test
    fun skipMovie_duringBulkProcessing_withPreloadedItem_advancesInPlace() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.currentBulkRecordId } returns 1L
        every { scanSessionHolder.bulkProcessingStopRequested } returns false
        every { scanSessionHolder.lastReviewFeatureType } returns FeatureType.MOVIE
        every { scanSessionHolder.bulkBatchLocation } returns ""
        every { scanSessionHolder.lastReviewLocation } returns ""
        val preloadedReview = PreloadedBulkReview(
            recordId = 2L,
            coverRelFilepath = "cover_2.jpg",
            coverAbsolutePath = "/tmp/cover_2.jpg",
            coverGuess = MovieGuess(title = "Next Title", year = "2021"),
            barcodeGuess = null,
            tmdbResults = listOf(
                TmdbSearchResult(
                    id = 2,
                    title = "Next Title",
                    year = "2021",
                    posterUrl = null,
                    tmdbUrl = "https://www.themoviedb.org/movie/2",
                ),
            ),
            capturedUpc = "2222222222222",
        )
        every { bulkReviewPreloadService.takePreloadedReview() } returns preloadedReview
        every { bulkImageRepository.resolveAbsolutePath("cover_2.jpg") } returns "/tmp/cover_2.jpg"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.skipMovie()
        advanceUntilIdle()

        coVerify(exactly = 0) { bulkImageRepository.markProcessed(any()) }
        assertTrue(bulkQueueSessionState.deferredRecordIds.contains(1L))
        io.mockk.verify {
            scanSessionHolder.startBulkItem(
                recordId = 2L,
                coverRelFilepath = "cover_2.jpg",
            )
        }
        io.mockk.verify { bulkReviewPreloadService.schedulePreloadAfter(2L) }
        io.mockk.verify(exactly = 0) { scanSessionHolder.finishScan() }
        assertEquals(false, viewModel.uiState.value.finished)
        assertEquals("Next Title", viewModel.uiState.value.title)
        assertEquals(1, viewModel.bulkReviewSessionKey.value)
    }

    @Test
    fun skipMovie_duringBulkProcessing_doesNotMarkProcessedAndResumesQueue() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.currentBulkRecordId } returns 42L
        every { scanSessionHolder.bulkProcessingStopRequested } returns false
        every { bulkReviewPreloadService.takePreloadedReview() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.skipMovie()
        advanceUntilIdle()

        coVerify(exactly = 0) { bulkImageRepository.markProcessed(any()) }
        io.mockk.verify { scanSessionHolder.finishBulkItem() }
        io.mockk.verify { scanSessionHolder.signalBulkQueueResume() }
        io.mockk.verify { scanSessionHolder.finishScan() }
        assertTrue(viewModel.uiState.value.finished)
        assertTrue(viewModel.uiState.value.finishedFromBulkProcessing)
    }

    @Test
    fun confirmDiscard_duringBulkProcessing_doesNotMarkProcessedAndResumesQueue() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.currentBulkRecordId } returns 42L
        every { scanSessionHolder.bulkProcessingStopRequested } returns false
        every { bulkReviewPreloadService.takePreloadedReview() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.confirmDiscard()
        advanceUntilIdle()

        coVerify(exactly = 0) { bulkImageRepository.markProcessed(any()) }
        io.mockk.verify { scanSessionHolder.finishBulkItem() }
        io.mockk.verify { scanSessionHolder.signalBulkQueueResume() }
        io.mockk.verify { scanSessionHolder.finishScan() }
        assertTrue(viewModel.uiState.value.finished)
        assertTrue(viewModel.uiState.value.finishedFromBulkProcessing)
    }

    @Test
    fun requestBulkRescan_startsBulkRescanForCurrentRecord() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.currentBulkRecordId } returns 42L

        val viewModel = createViewModel()
        val navigationEvents = mutableListOf<ReviewNavigationEvent>()
        val collectorJob = launch {
            viewModel.navigationEventFlow.collect { event ->
                navigationEvents.add(event)
            }
        }
        advanceUntilIdle()

        viewModel.requestBulkRescan()
        advanceUntilIdle()

        io.mockk.verify { scanSessionHolder.beginBulkRescan(42L) }
        assertEquals(listOf(ReviewNavigationEvent.NavigateToBulkRescan), navigationEvents)
        collectorJob.cancel()
    }

    @Test
    fun skipMovie_outsideBulkProcessing_doesNotTouchBulkRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.skipMovie()
        advanceUntilIdle()

        coVerify(exactly = 0) { bulkImageRepository.markProcessed(any()) }
        io.mockk.verify(exactly = 0) { scanSessionHolder.finishBulkItem() }
        io.mockk.verify(exactly = 0) { scanSessionHolder.signalBulkQueueResume() }
        io.mockk.verify { scanSessionHolder.finishScan() }
        assertTrue(viewModel.uiState.value.finished)
        assertEquals(false, viewModel.uiState.value.finishedFromBulkProcessing)
    }

}
