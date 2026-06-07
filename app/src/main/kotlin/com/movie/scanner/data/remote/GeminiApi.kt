package com.movie.scanner.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

interface GeminiApi {
    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String,
    ): GeminiModelsResponse
}

@JsonClass(generateAdapter = true)
data class GeminiModelsResponse(
    val models: List<GeminiModelJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GeminiModelJson(
    val name: String,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "supportedGenerationMethods") val supportedGenerationMethods: List<String> = emptyList(),
)
