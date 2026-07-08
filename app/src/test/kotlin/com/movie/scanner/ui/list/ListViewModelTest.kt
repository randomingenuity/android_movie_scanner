package com.movie.scanner.ui.list

import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.util.ListPagination
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class ListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val movieRepository = mockk<MovieRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { movieRepository.observeMovies() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_clearsLoadingAfterFirstMoviesEmission() = runTest {
        val viewModel = ListViewModel(movieRepository = movieRepository)

        assertTrue(viewModel.uiState.value.isLoadingMovies)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingMovies)
        assertTrue(viewModel.uiState.value.allMovies.isEmpty())
    }

    @Test
    fun showNextPage_advancesDisplayedMovies() = runTest {
        val movies = buildMovies(count = ListPagination.DEFAULT_PAGE_SIZE + 5)
        every { movieRepository.observeMovies() } returns flowOf(movies)

        val viewModel = ListViewModel(movieRepository = movieRepository)
        advanceUntilIdle()

        assertEquals(ListPagination.DEFAULT_PAGE_SIZE, viewModel.uiState.value.displayedMovies.size)
        assertTrue(viewModel.uiState.value.hasNextPage)

        viewModel.showNextPage()
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.displayedMovies.size)
        assertEquals(1, viewModel.uiState.value.currentPageIndex)
        assertFalse(viewModel.uiState.value.hasNextPage)
    }

    @Test
    fun selectLocationFilter_resetsToFirstPage() = runTest {
        val movies = buildMovies(count = ListPagination.DEFAULT_PAGE_SIZE + 5, location = "Shelf A") +
            buildMovies(
                count = 3,
                location = "Shelf B",
                idOffset = (ListPagination.DEFAULT_PAGE_SIZE + 5).toLong(),
            )
        every { movieRepository.observeMovies() } returns flowOf(movies)

        val viewModel = ListViewModel(movieRepository = movieRepository)
        advanceUntilIdle()

        viewModel.showNextPage()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentPageIndex)

        viewModel.selectLocationFilter("Shelf B")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.currentPageIndex)
        assertEquals(3, viewModel.uiState.value.displayedMovies.size)
        assertEquals("Shelf B", viewModel.uiState.value.selectedLocationFilter)
    }

    private fun buildMovies(
        count: Int,
        location: String? = null,
        idOffset: Long = 0,
    ): List<MovieEntity> = (0 until count).map { index ->
        val movieId = idOffset + index + 1
        MovieEntity(
            id = movieId,
            title = "Title $movieId",
            year = "2000",
            tmdbId = null,
            tmdbUrl = null,
            posterUrl = null,
            upc = null,
            isForceAdded = false,
            sortOrder = movieId.toInt(),
            location = location,
        )
    }
}
