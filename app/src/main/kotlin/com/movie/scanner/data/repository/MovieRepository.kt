package com.movie.scanner.data.repository

import com.movie.scanner.data.local.MovieDao
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.TmdbSearchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieRepository @Inject constructor(
    private val movieDao: MovieDao,
) {
    fun observeMovies(): Flow<List<MovieEntity>> = movieDao.observeMovies()

    suspend fun listMovies(): List<MovieEntity> = movieDao.listMovies()

    suspend fun countMovies(): Int = movieDao.countMovies()

    suspend fun existsByTmdbId(tmdbId: Int): Boolean = movieDao.existsByTmdbId(tmdbId)

    suspend fun existsByTitleAndYear(title: String, year: String): Boolean =
        movieDao.existsByTitleAndYear(title, year)

    suspend fun addMatchedMovie(
        title: String,
        year: String,
        upc: String?,
        match: TmdbSearchResult,
    ): Result<Long> = runCatching {
        val existingMovie = movieDao.findByTmdbId(match.id)
        if (existingMovie != null) {
            movieDao.update(
                existingMovie.copy(
                    title = title,
                    year = year,
                    tmdbId = match.id,
                    tmdbUrl = match.tmdbUrl,
                    posterUrl = match.posterUrl,
                    upc = upc,
                    isForceAdded = false,
                ),
            )
            existingMovie.id
        } else {
            movieDao.insert(
                MovieEntity(
                    title = title,
                    year = year,
                    tmdbId = match.id,
                    tmdbUrl = match.tmdbUrl,
                    posterUrl = match.posterUrl,
                    upc = upc,
                    isForceAdded = false,
                    sortOrder = nextSortOrder(),
                ),
            )
        }
    }

    suspend fun addForceMovie(
        title: String,
        year: String,
        upc: String?,
    ): Result<Long> = runCatching {
        val existingMovie = movieDao.findByTitleAndYear(title, year)
        if (existingMovie != null) {
            movieDao.update(
                existingMovie.copy(
                    title = title,
                    year = year,
                    tmdbId = null,
                    tmdbUrl = null,
                    posterUrl = null,
                    upc = upc,
                    isForceAdded = true,
                ),
            )
            existingMovie.id
        } else {
            movieDao.insert(
                MovieEntity(
                    title = title,
                    year = year,
                    tmdbId = null,
                    tmdbUrl = null,
                    posterUrl = null,
                    upc = upc,
                    isForceAdded = true,
                    sortOrder = nextSortOrder(),
                ),
            )
        }
    }

    suspend fun deleteMovie(movieId: Long) {
        movieDao.deleteById(movieId)
    }

    suspend fun clearAll() {
        movieDao.deleteAll()
    }

    private suspend fun nextSortOrder(): Int = (movieDao.maxSortOrder() ?: -1) + 1
}
