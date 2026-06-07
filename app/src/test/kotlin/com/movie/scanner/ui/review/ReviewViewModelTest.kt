package com.movie.scanner.ui.review

import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.data.repository.TmdbRepository
import com.movie.scanner.data.session.ScanSessionHolder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)
    private val tmdbRepository = mockk<TmdbRepository>(relaxed = true)
    private val movieRepository = mockk<MovieRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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
        coEvery { movieRepository.existsByTmdbId(any()) } returns false
        coEvery { movieRepository.existsByTitleAndYear(any(), any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_prefillsTitleAndYearFromCoverGuess() = runTest {
        val viewModel = ReviewViewModel(scanSessionHolder, tmdbRepository, movieRepository)
        advanceUntilIdle()

        assertEquals("Cover Title", viewModel.uiState.value.title)
        assertEquals("2020", viewModel.uiState.value.year)
        assertEquals("9781234567890", viewModel.uiState.value.barcode)
    }

    @Test
    fun init_fallsBackToBarcodeGuessWhenCoverMissing() = runTest {
        every { scanSessionHolder.coverGuess } returns null

        val viewModel = ReviewViewModel(scanSessionHolder, tmdbRepository, movieRepository)
        advanceUntilIdle()

        assertEquals("Barcode Title", viewModel.uiState.value.title)
        assertEquals("2019", viewModel.uiState.value.year)
    }

    @Test
    fun updateBarcode_stripsNewlines() = runTest {
        val viewModel = ReviewViewModel(scanSessionHolder, tmdbRepository, movieRepository)
        advanceUntilIdle()

        val sanitized = viewModel.updateBarcode("9781234567890\r\n")

        assertEquals("9781234567890", sanitized)
        assertEquals("9781234567890", viewModel.uiState.value.barcode)
    }

    @Test
    fun refreshActionState_showsReplaceWhenMovieAlreadyExists() = runTest {
        coEvery { movieRepository.existsByTmdbId(1) } returns true

        val viewModel = ReviewViewModel(scanSessionHolder, tmdbRepository, movieRepository)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.showReplaceAdd)
        assertEquals(
            "Already in list. Add will replace the existing entry.",
            viewModel.uiState.value.duplicateMessage,
        )
    }
}
