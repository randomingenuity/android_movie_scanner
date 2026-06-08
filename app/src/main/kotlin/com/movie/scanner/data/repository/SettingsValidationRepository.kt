package com.movie.scanner.data.repository

import com.movie.scanner.data.model.TmdbLanguageOption
import com.movie.scanner.data.remote.ClaudeApi
import com.movie.scanner.data.remote.GeminiApi
import com.movie.scanner.data.remote.OpenAiApi
import com.movie.scanner.data.remote.TmdbApi
import com.movie.scanner.util.ValidationErrorFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiValidationResult(
    val models: List<String>,
)

data class OpenAiValidationResult(
    val models: List<String>,
)

data class ClaudeValidationResult(
    val models: List<String>,
)

data class TmdbValidationResult(
    val languages: List<TmdbLanguageOption>,
)

@Singleton
class SettingsValidationRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val openAiApi: OpenAiApi,
    private val claudeApi: ClaudeApi,
    private val tmdbApi: TmdbApi,
) {
    suspend fun validateGeminiApiKey(apiKey: String): Result<GeminiValidationResult> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Gemini API key is empty."))
        }
        return try {
            val response = geminiApi.listModels(apiKey)
            val models = response.models
                .filter { model ->
                    "generateContent" in model.supportedGenerationMethods &&
                        model.name.contains("gemini", ignoreCase = true)
                }
                .map { model -> model.name.removePrefix("models/") }
                .sorted()
            if (models.isEmpty()) {
                Result.failure(IllegalStateException("No Gemini models available for this API key."))
            } else {
                Result.success(GeminiValidationResult(models = models))
            }
        } catch (exception: Exception) {
            Result.failure(Exception(ValidationErrorFormatter.format(exception), exception))
        }
    }

    suspend fun validateOpenAiApiKey(apiKey: String): Result<OpenAiValidationResult> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("OpenAI API key is empty."))
        }
        return try {
            val response = openAiApi.listModels(authorization = "Bearer $apiKey")
            val models = response.data
                .map { model -> model.id }
                .filter { modelId ->
                    modelId.startsWith("gpt-") || modelId.startsWith("o1") || modelId.startsWith("o3")
                }
                .sorted()
            if (models.isEmpty()) {
                Result.failure(IllegalStateException("No OpenAI chat models available for this API key."))
            } else {
                Result.success(OpenAiValidationResult(models = models))
            }
        } catch (exception: Exception) {
            Result.failure(Exception(ValidationErrorFormatter.format(exception), exception))
        }
    }

    suspend fun validateClaudeApiKey(apiKey: String): Result<ClaudeValidationResult> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Claude API key is empty."))
        }
        return try {
            val response = claudeApi.listModels(apiKey = apiKey)
            val models = response.data
                .map { model -> model.id }
                .filter { modelId -> modelId.startsWith("claude-") }
                .sorted()
            if (models.isEmpty()) {
                Result.failure(IllegalStateException("No Claude models available for this API key."))
            } else {
                Result.success(ClaudeValidationResult(models = models))
            }
        } catch (exception: Exception) {
            Result.failure(Exception(ValidationErrorFormatter.format(exception), exception))
        }
    }

    suspend fun validateTmdbApiKey(apiKey: String): Result<TmdbValidationResult> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("TMDB API key is empty."))
        }
        return try {
            tmdbApi.getConfiguration(apiKey)
            val languages = tmdbApi.getLanguages(apiKey)
                .map { language ->
                    TmdbLanguageOption(
                        code = language.iso6391,
                        displayName = language.englishName,
                    )
                }
                .sortedBy { option -> option.displayName }
            Result.success(TmdbValidationResult(languages = languages))
        } catch (exception: Exception) {
            Result.failure(Exception(ValidationErrorFormatter.format(exception), exception))
        }
    }
}
