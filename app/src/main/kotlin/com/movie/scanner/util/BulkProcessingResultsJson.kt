package com.movie.scanner.util

import com.movie.scanner.data.model.BulkProcessingResults
import kotlinx.serialization.json.Json

object BulkProcessingResultsJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(results: BulkProcessingResults): String =
        json.encodeToString(BulkProcessingResults.serializer(), results)

    fun parse(raw: String): BulkProcessingResults =
        json.decodeFromString(BulkProcessingResults.serializer(), raw)

    /**
     * True when recognition finished from barcode lookup alone (cover was not needed).
     */
    fun hasBarcodeResult(results: BulkProcessingResults): Boolean {
        if (results.recognitionError != null) {
            return false
        }
        if (results.tmdbResults.isEmpty()) {
            return false
        }
        if (results.coverGuess != null) {
            return false
        }
        return results.barcodeGuess?.title?.isNotBlank() == true
    }
}
