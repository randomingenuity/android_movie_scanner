package com.movie.scanner.data.model

import kotlinx.serialization.Serializable

/**
 * LLM and TMDB recognition output stored on a bulk queue row before review.
 */
@Serializable
data class BulkProcessingResults(
    val coverGuess: MovieGuess? = null,
    val barcodeGuess: MovieGuess? = null,
    val tmdbResults: List<TmdbSearchResult> = emptyList(),
    val capturedUpc: String? = null,
    val recognitionError: String? = null,
)
