package com.movie.scanner.util

import com.movie.scanner.data.model.MovieGuess
import kotlinx.serialization.json.Json

object MovieGuessJson {
    private val json = Json { ignoreUnknownKeys = true }

    val openAiSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "year" to mapOf("type" to "string"),
            "confidence" to mapOf("type" to "number"),
        ),
        "required" to listOf("title", "year"),
        "additionalProperties" to false,
    )

    fun parse(raw: String): MovieGuess {
        val trimmed = raw.trim()
        val jsonPayload = when {
            trimmed.startsWith("```") -> trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            else -> trimmed
        }
        return json.decodeFromString(MovieGuess.serializer(), jsonPayload)
    }
}
