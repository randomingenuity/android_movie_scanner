package com.movie.scanner.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("configuration")
    suspend fun getConfiguration(
        @Query("api_key") apiKey: String,
    ): TmdbConfigurationResponse

    @GET("configuration/languages")
    suspend fun getLanguages(
        @Query("api_key") apiKey: String,
    ): List<TmdbLanguageJson>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: String? = null,
        @Query("language") language: String,
    ): TmdbSearchResponse
}

@JsonClass(generateAdapter = true)
data class TmdbConfigurationResponse(
    val images: TmdbImagesConfiguration?,
)

@JsonClass(generateAdapter = true)
data class TmdbImagesConfiguration(
    @Json(name = "secure_base_url") val secureBaseUrl: String?,
)

@JsonClass(generateAdapter = true)
data class TmdbLanguageJson(
    @Json(name = "iso_639_1") val iso6391: String,
    @Json(name = "english_name") val englishName: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(
    val results: List<TmdbMovieJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TmdbMovieJson(
    val id: Int,
    val title: String,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "poster_path") val posterPath: String?,
)
