package com.movie.scanner.ui.review

import com.movie.scanner.data.model.BulkUnprocessedImageEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
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
class ReviewViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)
    private val tmdbRepository = mockk<TmdbRepository>(relaxed = true)
    private val movieRepository = mockk<MovieRepository>(relaxed = true)
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val bulkQueueSessionState = BulkQueueSessionState()
    private val bulkReviewPreloadService = mockk<BulkReviewPreloadService>(relaxed = true)
    private val reviewPayloadGenerationFlow = MutableStateFlow(1L)

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
        every { scanSessionHolder.lastReviewDiscType } returns null
        every { scanSessionHolder.lastReviewEdition } returns null
        every { scanSessionHolder.bulkBatchDiscType } returns null
        every { scanSessionHolder.isBulkProcessing } returns false
        every { scanSessionHolder.bulkCoverRelFilepath } returns null
        every { scanSessionHolder.reviewPayloadGeneration } returns reviewPayloadGenerationFlow
        coEvery { tmdbRepository.searchMovies(any(), any()) } returns Result.success(emptyList())
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
        assertEquals(
            ReviewAutomaticParameterSource.BARCODE_LOOKUP,
            viewModel.uiState.value.automaticParameterSource,
        )
    }

    @Test
    fun init_usesCoverImageParameterSourceWhenCoverProvidesTitle() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Cover Title", viewModel.uiState.value.extractedCoverTitle)
        assertEquals(
            ReviewAutomaticParameterSource.COVER_IMAGE,
            viewModel.uiState.value.automaticParameterSource,
        )
    }

    @Test
    fun buildParametersFromComment_reportsManualWhenTitleChanged() {
        val comment = ReviewViewModel.buildParametersFromComment(
            title = "Edited",
            year = "2020",
            barcode = "9781234567890",
            recognizedTitle = "Cover Title",
            recognizedYear = "2020",
            recognizedBarcode = "9781234567890",
            automaticParameterSource = ReviewAutomaticParameterSource.COVER_IMAGE,
        )

        assertEquals("manual", comment)
    }

    @Test
    fun buildParametersFromComment_reportsCoverImageWhenUnchanged() {
        val comment = ReviewViewModel.buildParametersFromComment(
            title = "Cover Title",
            year = "2020",
            barcode = "9781234567890",
            recognizedTitle = "Cover Title",
            recognizedYear = "2020",
            recognizedBarcode = "9781234567890",
            automaticParameterSource = ReviewAutomaticParameterSource.COVER_IMAGE,
        )

        assertEquals("cover image", comment)
    }

    @Test
    fun resolveMatchedTmdbResult_usesSelectedMatchWhenPresent() {
        val selected = TmdbSearchResult(
            id = 42,
            title = "Arrival",
            year = "2016",
            posterUrl = null,
            tmdbUrl = "https://www.themoviedb.org/movie/42",
        )
        val other = TmdbSearchResult(
            id = 99,
            title = "Other",
            year = "2015",
            posterUrl = null,
            tmdbUrl = "https://www.themoviedb.org/movie/99",
        )
        val uiState = ReviewUiState(
            selectedTmdbResult = selected,
            tmdbResults = listOf(selected, other),
        )

        assertEquals(42, ReviewViewModel.resolveMatchedTmdbResult(uiState)?.id)
    }

    @Test
    fun resolveMatchedTmdbResult_fallsBackToFirstResult() {
        val first = TmdbSearchResult(
            id = 7,
            title = "Arrival",
            year = "2016",
            posterUrl = null,
            tmdbUrl = "https://www.themoviedb.org/movie/7",
        )
        val uiState = ReviewUiState(
            tmdbResults = listOf(first),
            selectedTmdbResult = null,
        )

        assertEquals(7, ReviewViewModel.resolveMatchedTmdbResult(uiState)?.id)
    }

    @Test
    fun ensureInitialTmdbMatch_loadsSessionResultsIntoUiState() = runTest {
        every { scanSessionHolder.coverGuess } returns null
        every { scanSessionHolder.barcodeGuess } returns null
        every { scanSessionHolder.initialTmdbResults } returns emptyList()
        every { scanSessionHolder.resolveCapturedUpc() } returns null
        reviewPayloadGenerationFlow.value = 1L

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tmdbResults.isEmpty())

        every { scanSessionHolder.initialTmdbResults } returns listOf(
            TmdbSearchResult(
                id = 7,
                title = "Arrival",
                year = "2016",
                posterUrl = null,
                tmdbUrl = "https://www.themoviedb.org/movie/7",
            ),
        )
        reviewPayloadGenerationFlow.value = 2L
        viewModel.consumeReviewPayloadFromSessionIfNeeded()
        viewModel.ensureInitialTmdbMatch()
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.selectedTmdbResult?.id)
        assertEquals(1, viewModel.uiState.value.tmdbResults.size)
        assertEquals(7, viewModel.resolveMatchedTmdbResultForDisplay()?.id)
    }

    @Test
    fun ensureInitialTmdbMatch_searchesTmdbWhenTitleAndYearAreKnown() = runTest {
        every { scanSessionHolder.coverGuess } returns MovieGuess(title = "Arrival", year = "2016")
        every { scanSessionHolder.barcodeGuess } returns null
        every { scanSessionHolder.initialTmdbResults } returns emptyList()
        coEvery { tmdbRepository.searchMovies("Arrival", "2016") } returns Result.success(
            listOf(
                TmdbSearchResult(
                    id = 42,
                    title = "Arrival",
                    year = "2016",
                    posterUrl = null,
                    tmdbUrl = "https://www.themoviedb.org/movie/42",
                ),
            ),
        )

        val viewModel = createViewModel()
        viewModel.ensureInitialTmdbMatch()
        advanceUntilIdle()

        assertEquals(42, viewModel.uiState.value.selectedTmdbResult?.id)
        coVerify(exactly = 1) { tmdbRepository.searchMovies("Arrival", "2016") }
    }

    @Test
    fun resolveRequiresManualTitleEntry_trueWhenBarcodeAndCoverTitleMissing() {
        assertTrue(
            ReviewViewModel.resolveRequiresManualTitleEntry(
                coverGuess = null,
                capturedBarcode = null,
            ),
        )
        assertTrue(
            ReviewViewModel.resolveRequiresManualTitleEntry(
                coverGuess = MovieGuess(),
                capturedBarcode = "",
            ),
        )
    }

    @Test
    fun resolveRequiresManualTitleEntry_falseWhenBarcodeCaptured() {
        assertFalse(
            ReviewViewModel.resolveRequiresManualTitleEntry(
                coverGuess = null,
                capturedBarcode = "9781234567890",
            ),
        )
    }

    @Test
    fun resolveRequiresManualTitleEntry_falseWhenCoverTitlePresent() {
        assertFalse(
            ReviewViewModel.resolveRequiresManualTitleEntry(
                coverGuess = MovieGuess(title = "Arrival"),
                capturedBarcode = null,
            ),
        )
    }

    @Test
    fun init_setsRequiresManualTitleEntryWhenRecognitionFailed() = runTest {
        every { scanSessionHolder.coverGuess } returns MovieGuess()
        every { scanSessionHolder.barcodeGuess } returns null
        every { scanSessionHolder.initialTmdbResults } returns emptyList()
        every { scanSessionHolder.resolveCapturedUpc() } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.requiresManualTitleEntry)
        assertEquals("", viewModel.uiState.value.title)
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
    fun init_leavesDiscTypeEmptyDuringBulkProcessingUntilUserSelects() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.bulkBatchDiscType } returns "bluray"
        every { scanSessionHolder.lastReviewDiscType } returns "dvd"

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.discType)
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
            discType = "bluray",
            location = null,
        )
        coEvery { movieRepository.existsByTmdbId(1) } returns true
        coEvery { movieRepository.findByTmdbId(1) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Shelf A", viewModel.uiState.value.location)
        assertEquals("bluray", viewModel.uiState.value.discType)
    }

    @Test
    fun refreshActionState_leavesDiscTypeEmptyWhenDuplicateHasNoDiscType() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.bulkBatchDiscType } returns "dvd"
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
            discType = null,
            location = "Shelf B",
        )
        coEvery { movieRepository.existsByTmdbId(1) } returns true
        coEvery { movieRepository.findByTmdbId(1) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.discType)
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
            discType = "dvd",
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
        assertEquals("dvd", viewModel.uiState.value.discType)
        assertEquals("Shelf B", viewModel.uiState.value.location)
        assertEquals("2", viewModel.uiState.value.seasonNumberInput)
        assertEquals(4, viewModel.uiState.value.numberOfDiscsInput)
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
            discType = "bluray",
            location = "Shelf A",
        )
        coEvery { movieRepository.existsByTmdbId(1) } returns true
        coEvery { movieRepository.findByTmdbId(1) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("111111111111", viewModel.uiState.value.barcode)
        assertEquals("bluray", viewModel.uiState.value.discType)
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
    fun refreshActionState_enablesReplaceWhenExistingMovieMetadataPrefillsEdition() = runTest {
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
            discType = "bluray",
            edition = "theatrical",
            location = "Shelf A",
        )
        coEvery { movieRepository.existsByTmdbId(1) } returns true
        coEvery { movieRepository.findByTmdbId(1) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("theatrical", viewModel.uiState.value.edition)
        assertEquals("bluray", viewModel.uiState.value.discType)
        assertEquals(true, viewModel.actionState.value.showReplaceAdd)
        assertEquals(true, viewModel.actionState.value.isAddEnabled)
        assertEquals(null, viewModel.actionState.value.duplicateMessage)
        assertEquals(null, viewModel.actionState.value.addDisabledReason)
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
        assertEquals("Enter a season number.", viewModel.actionState.value.addDisabledReason)
    }

    @Test
    fun refreshActionState_requiresDiscTypeAndEditionBeforeAddEnabled() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.actionState.value.isAddEnabled)
        assertEquals(
            "Select main feature disc type.",
            viewModel.actionState.value.addDisabledReason,
        )

        viewModel.updateDiscType("bluray")
        advanceUntilIdle()

        assertEquals(false, viewModel.actionState.value.isAddEnabled)
        assertEquals("Select an edition.", viewModel.actionState.value.addDisabledReason)

        viewModel.updateEdition("theatrical")
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.isAddEnabled)
        assertEquals(null, viewModel.actionState.value.addDisabledReason)
    }

    @Test
    fun refreshActionState_showsReplaceWhenTelevisionTitleAndSeasonAlreadyExist() = runTest {
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
            discType = "dvd",
            location = "Shelf B",
            seasonNumber = 2,
            numberOfDiscs = 1,
        )
        coEvery { movieRepository.existsByTitleAndSeason("Cover Title", 2) } returns true
        coEvery { movieRepository.findByTitleAndSeason("Cover Title", 2) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSeasonNumberInput("2")
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.showReplaceAdd)
        assertEquals(true, viewModel.actionState.value.isAddEnabled)
        assertEquals(null, viewModel.actionState.value.addDisabledReason)
        assertEquals(null, viewModel.actionState.value.duplicateMessage)
    }

    @Test
    fun refreshActionState_hidesDuplicateMessageWhenMovieReplaceIsReady() = runTest {
        coEvery { movieRepository.existsByTmdbId(1) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateDiscType("bluray")
        viewModel.updateEdition("theatrical")
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.showReplaceAdd)
        assertEquals(true, viewModel.actionState.value.isAddEnabled)
        assertEquals(null, viewModel.actionState.value.duplicateMessage)
    }

    @Test
    fun refreshActionState_addDisabledReasonExplainsTelevisionDuplicateUntilDiscTypeSelected() = runTest {
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
            discType = null,
            location = "Shelf B",
            seasonNumber = 2,
            numberOfDiscs = 1,
        )
        coEvery { movieRepository.existsByTitleAndSeason("Cover Title", 2) } returns true
        coEvery { movieRepository.findByTitleAndSeason("Cover Title", 2) } returns existingMovie

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSeasonNumberInput("2")
        advanceUntilIdle()

        assertEquals(FeatureType.TV, viewModel.uiState.value.featureType)
        assertEquals(true, viewModel.actionState.value.showReplaceAdd)
        assertEquals(false, viewModel.actionState.value.isAddEnabled)
        assertEquals(
            "This title and season are already in the list. Select main feature disc type to use Replace.",
            viewModel.actionState.value.addDisabledReason,
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
        assertEquals(true, viewModel.actionState.value.isForceAddEnabled)
        assertEquals(null, viewModel.actionState.value.duplicateMessage)
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
    fun init_setsTmdbSyncedTitleAndYearFromPrefilledValues() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Cover Title", viewModel.uiState.value.tmdbSyncedTitle)
        assertEquals("2020", viewModel.uiState.value.tmdbSyncedYear)
    }

    @Test
    fun scheduleTitleUpdate_leavesTmdbSyncedValuesUnchanged() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.scheduleTitleUpdate("Edited Title")
        advanceUntilIdle()

        assertEquals("Edited Title", viewModel.uiState.value.title)
        assertEquals("Cover Title", viewModel.uiState.value.tmdbSyncedTitle)
    }

    @Test
    fun searchTmdb_updatesTmdbSyncedTitleAndYear() = runTest {
        coEvery { tmdbRepository.searchMovies("New Title", "2021") } returns Result.success(
            listOf(
                TmdbSearchResult(
                    id = 99,
                    title = "New Title",
                    year = "2021",
                    posterUrl = null,
                    tmdbUrl = "https://www.themoviedb.org/movie/99",
                ),
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.searchTmdb(
            ReviewFormFields(
                title = "New Title",
                year = "2021",
                barcode = "9781234567890",
                location = "",
                seasonNumberInput = "",
                numberOfDiscsInput = 1,
            ),
        )
        advanceUntilIdle()

        assertEquals("New Title", viewModel.uiState.value.tmdbSyncedTitle)
        assertEquals("2021", viewModel.uiState.value.tmdbSyncedYear)
        assertEquals(99, viewModel.uiState.value.selectedTmdbResult?.id)
    }

    @Test
    fun selectTmdbResult_updatesTmdbSyncedTitleAndYear() = runTest {
        every { scanSessionHolder.initialTmdbResults } returns listOf(
            TmdbSearchResult(
                id = 1,
                title = "Cover Title",
                year = "2020",
                posterUrl = null,
                tmdbUrl = "https://www.themoviedb.org/movie/1",
            ),
            TmdbSearchResult(
                id = 2,
                title = "Alternate Title",
                year = "2019",
                posterUrl = null,
                tmdbUrl = "https://www.themoviedb.org/movie/2",
            ),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val alternateResult = viewModel.uiState.value.tmdbResults[1]
        viewModel.selectTmdbResult(alternateResult)
        advanceUntilIdle()

        assertEquals("Alternate Title", viewModel.uiState.value.tmdbSyncedTitle)
        assertEquals("2019", viewModel.uiState.value.tmdbSyncedYear)
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

    @Test
    fun refreshActionState_enablesBackDuringBulkProcessingWhenLastAddedMovieExists() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        bulkQueueSessionState.rememberLastAddedEntry(movieId = 7L, bulkRecordId = 1L)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.actionState.value.isBackEnabled)
    }

    @Test
    fun refreshActionState_disablesBackWhenNoLastAddedMovie() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.actionState.value.isBackEnabled)
    }

    @Test
    fun goBackToLastAddedMovie_defersCurrentRecordAndLoadsLastAddedMovie() = runTest {
        every { scanSessionHolder.isBulkProcessing } returns true
        every { scanSessionHolder.currentBulkRecordId } returns 2L
        bulkQueueSessionState.rememberLastAddedEntry(movieId = 7L, bulkRecordId = 1L)
        val existingMovie = MovieEntity(
            id = 7L,
            title = "Saved Title",
            year = "2018",
            tmdbId = 5,
            tmdbUrl = "https://www.themoviedb.org/movie/5",
            posterUrl = null,
            upc = "3333333333333",
            isForceAdded = false,
            sortOrder = 0,
            featureType = FeatureType.MOVIE.label,
            discType = "dvd",
            location = "Shelf B",
        )
        coEvery { movieRepository.findById(7L) } returns existingMovie
        coEvery { movieRepository.existsByTmdbId(5) } returns true
        coEvery { movieRepository.findByTmdbId(5) } returns existingMovie
        coEvery { bulkImageRepository.getRecordById(1L) } returns BulkUnprocessedImageEntity(
            id = 1L,
            createdAtTimestamp = 0L,
            barcodeRelFilepath = "barcode_1.jpg",
            coverRelFilepath = "cover_1.jpg",
            wasProcessed = true,
        )
        every { bulkImageRepository.resolveAbsolutePath("cover_1.jpg") } returns "/tmp/cover_1.jpg"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.goBackToLastAddedMovie()
        advanceUntilIdle()

        assertTrue(bulkQueueSessionState.deferredRecordIds.contains(2L))
        assertEquals(null, bulkQueueSessionState.lastAddedMovieId)
        assertEquals(1L, bulkQueueSessionState.processingRecordId)
        io.mockk.verify { bulkReviewPreloadService.clearPreload() }
        io.mockk.verify {
            scanSessionHolder.startBulkItem(
                recordId = 1L,
                coverRelFilepath = "cover_1.jpg",
            )
        }
        io.mockk.verify { bulkReviewPreloadService.schedulePreloadAfter(1L) }
        assertEquals("Saved Title", viewModel.uiState.value.title)
        assertEquals("2018", viewModel.uiState.value.year)
        assertEquals("3333333333333", viewModel.uiState.value.barcode)
        assertEquals("dvd", viewModel.uiState.value.discType)
        assertEquals("Shelf B", viewModel.uiState.value.location)
        assertEquals("/tmp/cover_1.jpg", viewModel.uiState.value.bulkCoverAbsolutePath)
        assertEquals(false, viewModel.actionState.value.isBackEnabled)
        assertEquals(1, viewModel.bulkReviewSessionKey.value)
    }

}
