package com.movie.scanner.ui.list

import com.movie.scanner.data.repository.MovieRepository
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
        assertTrue(viewModel.uiState.value.movies.isEmpty())
    }
}
