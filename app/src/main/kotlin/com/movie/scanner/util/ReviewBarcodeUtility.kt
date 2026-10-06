package com.movie.scanner.util

import com.movie.scanner.data.model.BulkUnprocessedImageEntity

/**
 * Strips line breaks from barcode text entered or read during review and bulk scan.
 */
fun normalizeReviewBarcode(value: String): String =
    value.filter { character -> character != '\n' && character != '\r' }

/**
 * Returns the normalized UPC from bulk recognition JSON when present.
 */
fun resolveNormalizedCapturedUpc(processingResultsJson: String?): String? {
    if (processingResultsJson.isNullOrBlank()) {
        return null
    }
    val capturedUpc = BulkProcessingResultsJson.parse(processingResultsJson).capturedUpc
    if (capturedUpc.isNullOrBlank()) {
        return null
    }
    return normalizeReviewBarcode(capturedUpc).takeIf { normalizedUpc -> normalizedUpc.isNotBlank() }
}

/**
 * Returns the normalized UPC from a bulk queue row when recognition stored one.
 */
fun resolveNormalizedCapturedUpc(record: BulkUnprocessedImageEntity): String? =
    resolveNormalizedCapturedUpc(record.processingResultsJson)
