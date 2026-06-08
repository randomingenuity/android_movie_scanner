package com.movie.scanner.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.LlmProvider
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.remote.ClaudeApi
import com.movie.scanner.data.remote.ClaudeContentPart
import com.movie.scanner.data.remote.ClaudeImageSource
import com.movie.scanner.data.remote.ClaudeMessage
import com.movie.scanner.data.remote.ClaudeMessageRequest
import com.movie.scanner.data.remote.OpenAiApi
import com.movie.scanner.data.remote.OpenAiChatRequest
import com.movie.scanner.data.remote.OpenAiContentPart
import com.movie.scanner.data.remote.OpenAiImageUrl
import com.movie.scanner.data.remote.OpenAiJsonSchema
import com.movie.scanner.data.remote.OpenAiMessage
import com.movie.scanner.data.remote.OpenAiResponseFormat
import com.movie.scanner.util.MovieGuessJson
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRecognitionRepository @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val openAiApi: OpenAiApi,
    private val claudeApi: ClaudeApi,
) {
    suspend fun validateGeminiApiKey(apiKey: String): Result<Unit> = runCatching {
        val model = GenerativeModel(
            modelName = apiKeyStore.resolveGeminiModel(),
            apiKey = apiKey,
        )
        model.generateContent("Reply with OK")
    }

    suspend fun validateOpenAiApiKey(apiKey: String): Result<Unit> = runCatching {
        openAiApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = OpenAiChatRequest(
                model = ApiKeyStore.DEFAULT_OPENAI_MODEL,
                messages = listOf(
                    OpenAiMessage(
                        role = "user",
                        content = listOf(OpenAiContentPart(type = "text", text = "Reply with OK")),
                    ),
                ),
                responseFormat = OpenAiResponseFormat(
                    jsonSchema = OpenAiJsonSchema(
                        name = "ack",
                        schema = mapOf(
                            "type" to "object",
                            "properties" to mapOf("status" to mapOf("type" to "string")),
                            "required" to listOf("status"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
            ),
        )
    }

    suspend fun validateClaudeApiKey(apiKey: String): Result<Unit> = runCatching {
        claudeApi.createMessage(
            apiKey = apiKey,
            request = ClaudeMessageRequest(
                model = ApiKeyStore.DEFAULT_CLAUDE_MODEL,
                maxTokens = 16,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = listOf(ClaudeContentPart(type = "text", text = "Reply with OK")),
                    ),
                ),
            ),
        )
    }

    suspend fun recognizeCover(bitmap: Bitmap): Result<MovieGuess> {
        return recognizeWithFallback(
            buildPrompt = { provider ->
                when (provider) {
                    LlmProvider.GEMINI -> buildCoverPromptGemini(bitmap)
                    LlmProvider.OPENAI -> buildCoverPromptOpenAi(bitmap)
                    LlmProvider.CLAUDE -> buildCoverPromptClaude(bitmap)
                }
            },
        )
    }

    suspend fun recognizeBarcode(
        upc: String,
        barcodeBitmap: Bitmap?,
    ): Result<MovieGuess> {
        return recognizeWithFallback(
            buildPrompt = { provider ->
                when (provider) {
                    LlmProvider.GEMINI -> buildBarcodePromptGemini(upc, barcodeBitmap)
                    LlmProvider.OPENAI -> buildBarcodePromptOpenAi(upc, barcodeBitmap)
                    LlmProvider.CLAUDE -> buildBarcodePromptClaude(upc, barcodeBitmap)
                }
            },
        )
    }

    private suspend fun recognizeWithFallback(
        buildPrompt: suspend (LlmProvider) -> Result<MovieGuess>,
    ): Result<MovieGuess> {
        val preferred = apiKeyStore.getPreferredLlmProvider()
        val providerOrder = listOf(preferred) + LlmProvider.entries.filter { provider -> provider != preferred }
        var lastFailure: Result<MovieGuess>? = null
        for (provider in providerOrder) {
            if (!apiKeyStore.hasLlmProvider(provider)) {
                continue
            }
            val result = invokeProvider(provider, buildPrompt)
            if (result.isSuccess) {
                return result
            }
            lastFailure = result
        }
        return lastFailure ?: Result.failure(IllegalStateException("No LLM provider is configured."))
    }

    private suspend fun invokeProvider(
        provider: LlmProvider,
        buildPrompt: suspend (LlmProvider) -> Result<MovieGuess>,
    ): Result<MovieGuess> {
        if (!apiKeyStore.hasLlmProvider(provider)) {
            return Result.failure(IllegalStateException("${provider.displayName} API key is not configured."))
        }
        val firstAttempt = buildPrompt(provider)
        if (firstAttempt.isSuccess && firstAttempt.getOrNull()?.title?.isNotBlank() == true) {
            return firstAttempt
        }
        return buildPrompt(provider)
    }

    private suspend fun buildCoverPromptGemini(bitmap: Bitmap): Result<MovieGuess> = runCatching {
        val apiKey = apiKeyStore.getGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key is not configured.")
        val model = GenerativeModel(
            modelName = apiKeyStore.resolveGeminiModel(),
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
        )
        val response = model.generateContent(
            content {
                image(bitmap)
                text(COVER_PROMPT)
            },
        )
        MovieGuessJson.parse(response.text ?: throw IllegalStateException("Empty Gemini response."))
    }

    private suspend fun buildCoverPromptOpenAi(bitmap: Bitmap): Result<MovieGuess> = runCatching {
        val apiKey = apiKeyStore.getOpenAiApiKey()
            ?: throw IllegalStateException("OpenAI API key is not configured.")
        val imageDataUrl = encodeBitmap(bitmap)
        val response = openAiApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = OpenAiChatRequest(
                model = apiKeyStore.getOpenAiModel(),
                messages = listOf(
                    OpenAiMessage(
                        role = "user",
                        content = listOf(
                            OpenAiContentPart(type = "text", text = COVER_PROMPT),
                            OpenAiContentPart(
                                type = "image_url",
                                imageUrl = OpenAiImageUrl(url = imageDataUrl),
                            ),
                        ),
                    ),
                ),
                responseFormat = OpenAiResponseFormat(
                    jsonSchema = OpenAiJsonSchema(
                        name = "movie_guess",
                        schema = MovieGuessJson.openAiSchema,
                    ),
                ),
            ),
        )
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty OpenAI response.")
        MovieGuessJson.parse(content)
    }

    private suspend fun buildBarcodePromptGemini(
        upc: String,
        barcodeBitmap: Bitmap?,
    ): Result<MovieGuess> = runCatching {
        val apiKey = apiKeyStore.getGeminiApiKey()
            ?: throw IllegalStateException("Gemini API key is not configured.")
        val model = GenerativeModel(
            modelName = apiKeyStore.resolveGeminiModel(),
            apiKey = apiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
            },
        )
        val response = if (barcodeBitmap != null) {
            model.generateContent(
                content {
                    image(barcodeBitmap)
                    text(buildBarcodePrompt(upc))
                },
            )
        } else {
            model.generateContent(buildBarcodePrompt(upc))
        }
        MovieGuessJson.parse(response.text ?: throw IllegalStateException("Empty Gemini response."))
    }

    private suspend fun buildBarcodePromptOpenAi(
        upc: String,
        barcodeBitmap: Bitmap?,
    ): Result<MovieGuess> = runCatching {
        val apiKey = apiKeyStore.getOpenAiApiKey()
            ?: throw IllegalStateException("OpenAI API key is not configured.")
        val contentParts = mutableListOf(
            OpenAiContentPart(type = "text", text = buildBarcodePrompt(upc)),
        )
        if (barcodeBitmap != null) {
            contentParts.add(
                OpenAiContentPart(
                    type = "image_url",
                    imageUrl = OpenAiImageUrl(url = encodeBitmap(barcodeBitmap)),
                ),
            )
        }
        val response = openAiApi.createChatCompletion(
            authorization = "Bearer $apiKey",
            request = OpenAiChatRequest(
                model = apiKeyStore.getOpenAiModel(),
                messages = listOf(
                    OpenAiMessage(
                        role = "user",
                        content = contentParts,
                    ),
                ),
                responseFormat = OpenAiResponseFormat(
                    jsonSchema = OpenAiJsonSchema(
                        name = "movie_guess",
                        schema = MovieGuessJson.openAiSchema,
                    ),
                ),
            ),
        )
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty OpenAI response.")
        MovieGuessJson.parse(content)
    }

    private suspend fun buildCoverPromptClaude(bitmap: Bitmap): Result<MovieGuess> = runCatching {
        val apiKey = apiKeyStore.getClaudeApiKey()
            ?: throw IllegalStateException("Claude API key is not configured.")
        val response = claudeApi.createMessage(
            apiKey = apiKey,
            request = ClaudeMessageRequest(
                model = apiKeyStore.getClaudeModel(),
                maxTokens = 1024,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = listOf(
                            ClaudeContentPart(
                                type = "image",
                                source = ClaudeImageSource(
                                    type = "base64",
                                    mediaType = "image/jpeg",
                                    data = encodeBitmapBase64(bitmap),
                                ),
                            ),
                            ClaudeContentPart(type = "text", text = COVER_PROMPT),
                        ),
                    ),
                ),
            ),
        )
        val content = response.content.firstOrNull { part -> part.type == "text" }?.text
            ?: throw IllegalStateException("Empty Claude response.")
        MovieGuessJson.parse(content)
    }

    private suspend fun buildBarcodePromptClaude(
        upc: String,
        barcodeBitmap: Bitmap?,
    ): Result<MovieGuess> = runCatching {
        val apiKey = apiKeyStore.getClaudeApiKey()
            ?: throw IllegalStateException("Claude API key is not configured.")
        val contentParts = mutableListOf(
            ClaudeContentPart(type = "text", text = buildBarcodePrompt(upc)),
        )
        if (barcodeBitmap != null) {
            contentParts.add(
                ClaudeContentPart(
                    type = "image",
                    source = ClaudeImageSource(
                        type = "base64",
                        mediaType = "image/jpeg",
                        data = encodeBitmapBase64(barcodeBitmap),
                    ),
                ),
            )
        }
        val response = claudeApi.createMessage(
            apiKey = apiKey,
            request = ClaudeMessageRequest(
                model = apiKeyStore.getClaudeModel(),
                maxTokens = 1024,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = contentParts,
                    ),
                ),
            ),
        )
        val content = response.content.firstOrNull { part -> part.type == "text" }?.text
            ?: throw IllegalStateException("Empty Claude response.")
        MovieGuessJson.parse(content)
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val encoded = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun encodeBitmapBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildBarcodePrompt(upc: String): String =
        """
        A DVD, Blu-ray, or book case has product barcode (UPC, EAN, or ISBN): $upc.
        Infer the most likely movie title and release year for this product barcode.
        Return JSON with title, year, and confidence between 0 and 1.
        If year is unknown, return an empty string for year.
        """.trimIndent()

    companion object {
        private const val COVER_PROMPT =
            "Identify the movie from this cover photo. Return only JSON with keys title, year, and confidence between 0 and 1. If year is unknown, return an empty string for year."
    }
}
