package com.movie.scanner.data.repository

import com.movie.scanner.data.local.MovieDao
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.TmdbSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovieRepositoryTest {
    private val movieDao = mockk<MovieDao>(relaxed = true)
    private lateinit var movieRepository: MovieRepository

    @Before
    fun setUp() {
        movieRepository = MovieRepository(movieDao)
    }

    @Test
    fun addMatchedMovie_insertsNewMovieWithNextSortOrder() = runTest {
        val match = TmdbSearchResult(
            id = 42,
            title = "The Matrix",
            year = "1999",
            posterUrl = "https://image.tmdb.org/poster.jpg",
            tmdbUrl = "https://www.themoviedb.org/movie/42",
        )
        coEvery { movieDao.findByTmdbId(42) } returns null
        coEvery { movieDao.maxSortOrder() } returns 2
        coEvery { movieDao.insert(any()) } returns 10L

        val result = movieRepository.addMatchedMovie(
            title = "The Matrix",
            year = "1999",
            upc = "012345678905",
            match = match,
        )

        assertTrue(result.isSuccess)
        assertEquals(10L, result.getOrNull())
        val insertedMovie = slot<MovieEntity>()
        coVerify {
            movieDao.insert(capture(insertedMovie))
        }
        assertEquals("The Matrix", insertedMovie.captured.title)
        assertEquals(3, insertedMovie.captured.sortOrder)
        assertEquals(false, insertedMovie.captured.isForceAdded)
    }

    @Test
    fun addMatchedMovie_replacesExistingMovieAndPreservesSortOrder() = runTest {
        val existingMovie = MovieEntity(
            id = 7L,
            title = "Old Title",
            year = "1999",
            tmdbId = 42,
            tmdbUrl = "https://www.themoviedb.org/movie/42",
            posterUrl = null,
            upc = null,
            isForceAdded = false,
            sortOrder = 5,
        )
        val match = TmdbSearchResult(
            id = 42,
            title = "The Matrix",
            year = "1999",
            posterUrl = "https://image.tmdb.org/poster.jpg",
            tmdbUrl = "https://www.themoviedb.org/movie/42",
        )
        coEvery { movieDao.findByTmdbId(42) } returns existingMovie

        val result = movieRepository.addMatchedMovie(
            title = "The Matrix (Remastered)",
            year = "1999",
            upc = "012345678905",
            match = match,
        )

        assertTrue(result.isSuccess)
        assertEquals(7L, result.getOrNull())
        val updatedMovie = slot<MovieEntity>()
        coVerify {
            movieDao.update(capture(updatedMovie))
        }
        assertEquals("The Matrix (Remastered)", updatedMovie.captured.title)
        assertEquals(5, updatedMovie.captured.sortOrder)
        assertEquals("012345678905", updatedMovie.captured.upc)
        coVerify(exactly = 0) { movieDao.insert(any()) }
    }

    @Test
    fun addForceMovie_replacesExistingTitleAndYearMatch() = runTest {
        val existingMovie = MovieEntity(
            id = 3L,
            title = "Mystery Box",
            year = "2001",
            tmdbId = 99,
            tmdbUrl = "https://www.themoviedb.org/movie/99",
            posterUrl = "https://image.tmdb.org/old.jpg",
            upc = null,
            isForceAdded = false,
            sortOrder = 1,
        )
        coEvery { movieDao.findByTitleAndYear("Mystery Box", "2001") } returns existingMovie

        val result = movieRepository.addForceMovie(
            title = "Mystery Box",
            year = "2001",
            upc = "9781234567890",
        )

        assertTrue(result.isSuccess)
        val updatedMovie = slot<MovieEntity>()
        coVerify {
            movieDao.update(capture(updatedMovie))
        }
        assertEquals(true, updatedMovie.captured.isForceAdded)
        assertEquals(null, updatedMovie.captured.tmdbId)
        assertEquals("9781234567890", updatedMovie.captured.upc)
    }
}
