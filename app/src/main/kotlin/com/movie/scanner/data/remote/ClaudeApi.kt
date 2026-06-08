package com.movie.scanner.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ClaudeApi {
    @GET("v1/models")
    suspend fun listModels(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") anthropicVersion: String = ANTHROPIC_VERSION,
    ): ClaudeModelsResponse

    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") anthropicVersion: String = ANTHROPIC_VERSION,
        @Body request: ClaudeMessageRequest,
    ): ClaudeMessageResponse

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

@JsonClass(generateAdapter = true)
data class ClaudeMessageRequest(
    val model: String,
    @Json(name = "max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>,
    val temperature: Double = 0.2,
)

@JsonClass(generateAdapter = true)
data class ClaudeMessage(
    val role: String,
    val content: List<ClaudeContentPart>,
)

@JsonClass(generateAdapter = true)
data class ClaudeContentPart(
    val type: String,
    val text: String? = null,
    val source: ClaudeImageSource? = null,
)

@JsonClass(generateAdapter = true)
data class ClaudeImageSource(
    val type: String,
    @Json(name = "media_type") val mediaType: String,
    val data: String,
)

@JsonClass(generateAdapter = true)
data class ClaudeModelsResponse(
    val data: List<ClaudeModelJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ClaudeModelJson(
    val id: String,
)

@JsonClass(generateAdapter = true)
data class ClaudeMessageResponse(
    val content: List<ClaudeResponseContent> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ClaudeResponseContent(
    val type: String,
    val text: String? = null,
)
