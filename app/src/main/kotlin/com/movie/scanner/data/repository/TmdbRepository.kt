package com.movie.scanner.data.repository

import android.content.Context
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.remote.TmdbApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val apiKeyStore: ApiKeyStore,
    @ApplicationContext private val context: Context,
) {
    suspend fun validateApiKey(apiKey: String): Result<Unit> = runCatching {
        tmdbApi.getConfiguration(apiKey)
    }

    suspend fun searchMovies(title: String, year: String?): Result<List<TmdbSearchResult>> {
        val apiKey = apiKeyStore.getTmdbApiKey()
            ?: return Result.failure(IllegalStateException("TMDB API key is not configured."))
        val language = apiKeyStore.getTmdbLanguageOverride()
            ?: Locale.getDefault().toLanguageTag().replace('-', '_')
        return runCatching {
            val response = tmdbApi.searchMovies(
                apiKey = apiKey,
                query = title,
                year = year?.takeIf { it.isNotBlank() },
                language = language,
            )
            val configuration = tmdbApi.getConfiguration(apiKey)
            val posterBaseUrl = configuration.images?.secureBaseUrl ?: "https://image.tmdb.org/t/p/w342"
            response.results.map { movie ->
                val releaseYear = movie.releaseDate?.take(4).orEmpty()
                val posterUrl = movie.posterPath?.let { posterBaseUrl + it }
                TmdbSearchResult(
                    id = movie.id,
                    title = movie.title,
                    year = releaseYear,
                    posterUrl = posterUrl,
                    tmdbUrl = "https://www.themoviedb.org/movie/${movie.id}",
                )
            }
        }
    }
}
