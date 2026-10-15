package com.movie.scanner.util

import com.movie.scanner.data.model.MovieGuess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object MovieGuessJson {
    private val json = Json { ignoreUnknownKeys = true }

    val openAiSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "year" to mapOf("type" to "string"),
            "confidence" to mapOf("type" to "number"),
            "above_the_title" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
            ),
        ),
        "required" to listOf("title", "year", "above_the_title"),
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
        val objectElement = json.parseToJsonElement(jsonPayload) as JsonObject
        val decoded = json.decodeFromJsonElement(MovieGuess.serializer(), objectElement)
        val aboveTheTitle = parseAboveTheTitleElement(objectElement["above_the_title"])

        return MovieGuess(
            title = decoded.title,
            year = decoded.year,
            confidence = decoded.confidence,
            aboveTheTitle = aboveTheTitle,
        )
    }

    private fun parseAboveTheTitleElement(element: JsonElement?): String {
        if (element == null) {
            return ""
        }
        if (element is JsonArray) {
            val names = element.mapNotNull { item ->
                if (item is JsonPrimitive && item.isString) {
                    item.content.trim()
                } else {
                    null
                }
            }.filter { name -> name.isNotBlank() }

            return names.joinToString(", ")
        }
        if (element is JsonPrimitive && element.isString) {
            return element.content.trim()
        }

        return ""
    }
}
