package com.movie.scanner.util

import com.movie.scanner.data.model.BulkProcessingResults
import kotlinx.serialization.json.Json

object BulkProcessingResultsJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(results: BulkProcessingResults): String =
        json.encodeToString(BulkProcessingResults.serializer(), results)

    fun parse(raw: String): BulkProcessingResults =
        json.decodeFromString(BulkProcessingResults.serializer(), raw)
}
