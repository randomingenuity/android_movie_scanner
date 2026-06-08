package com.movie.scanner.data.repository

import com.movie.scanner.data.local.MovieDao
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.ReviewItemDetails
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

    suspend fun findByTmdbId(tmdbId: Int): MovieEntity? = movieDao.findByTmdbId(tmdbId)

    suspend fun findByTitleAndYear(title: String, year: String): MovieEntity? =
        movieDao.findByTitleAndYear(title, year)

    suspend fun existsByTitleAndSeason(title: String, seasonNumber: Int): Boolean =
        movieDao.existsByTitleAndSeason(title, seasonNumber)

    suspend fun findByTitleAndSeason(title: String, seasonNumber: Int): MovieEntity? =
        movieDao.findByTitleAndSeason(title, seasonNumber)

    suspend fun addMatchedMovie(
        title: String,
        year: String,
        upc: String?,
        match: TmdbSearchResult,
        details: ReviewItemDetails,
    ): Result<Long> = runCatching {
        val existingMovie = if (details.featureType == FeatureType.TV) {
            val seasonNumber = requireNotNull(details.seasonNumber) {
                "Season is required for TV entries."
            }
            movieDao.findByTitleAndSeason(title, seasonNumber)
        } else {
            movieDao.findByTmdbId(match.id)
        }
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
                    featureType = details.featureType.label,
                    discType = details.discType,
                    location = details.location,
                    seasonNumber = details.seasonNumber,
                    numberOfDiscs = details.numberOfDiscs,
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
                    featureType = details.featureType.label,
                    discType = details.discType,
                    location = details.location,
                    seasonNumber = details.seasonNumber,
                    numberOfDiscs = details.numberOfDiscs,
                ),
            )
        }
    }

    suspend fun addForceMovie(
        title: String,
        year: String,
        upc: String?,
        details: ReviewItemDetails,
    ): Result<Long> = runCatching {
        val existingMovie = if (details.featureType == FeatureType.TV) {
            val seasonNumber = requireNotNull(details.seasonNumber) {
                "Season is required for TV entries."
            }
            movieDao.findByTitleAndSeason(title, seasonNumber)
        } else {
            movieDao.findByTitleAndYear(title, year)
        }
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
                    featureType = details.featureType.label,
                    discType = details.discType,
                    location = details.location,
                    seasonNumber = details.seasonNumber,
                    numberOfDiscs = details.numberOfDiscs,
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
                    featureType = details.featureType.label,
                    discType = details.discType,
                    location = details.location,
                    seasonNumber = details.seasonNumber,
                    numberOfDiscs = details.numberOfDiscs,
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
