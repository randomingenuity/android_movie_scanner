package com.movie.scanner.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAiApi {
    @GET("models")
    suspend fun listModels(
        @Header("Authorization") authorization: String,
    ): OpenAiModelsResponse

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiChatRequest,
    ): OpenAiChatResponse
}

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @Json(name = "response_format") val responseFormat: OpenAiResponseFormat,
    val temperature: Double = 0.2,
)

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    val role: String,
    val content: List<OpenAiContentPart>,
)

@JsonClass(generateAdapter = true)
data class OpenAiContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: OpenAiImageUrl? = null,
)

@JsonClass(generateAdapter = true)
data class OpenAiImageUrl(
    val url: String,
)

@JsonClass(generateAdapter = true)
data class OpenAiResponseFormat(
    val type: String = "json_schema",
    @Json(name = "json_schema") val jsonSchema: OpenAiJsonSchema,
)

@JsonClass(generateAdapter = true)
data class OpenAiJsonSchema(
    val name: String,
    val schema: Map<String, Any>,
    val strict: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class OpenAiModelsResponse(
    val data: List<OpenAiModelJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OpenAiModelJson(
    val id: String,
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    val message: OpenAiTextMessage?,
)

@JsonClass(generateAdapter = true)
data class OpenAiTextMessage(
    val content: String?,
)
