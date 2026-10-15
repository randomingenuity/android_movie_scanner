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
     * True when TMDB was matched from barcode lookup (title/year on review come from barcode).
     */
    fun hasBarcodeResult(results: BulkProcessingResults): Boolean {
        if (results.recognitionError != null) {
            return false
        }
        if (results.tmdbResults.isEmpty()) {
            return false
        }
        if (!results.tmdbMatchedViaBarcode) {
            return false
        }
        return results.barcodeGuess?.title?.isNotBlank() == true
    }
}
