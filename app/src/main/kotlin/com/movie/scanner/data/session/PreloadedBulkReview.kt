package com.movie.scanner.data.session

import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult

/**
 * Recognition payload and cover path for the next bulk queue item, prepared before review.
 */
data class PreloadedBulkReview(
    val recordId: Long,
    val coverRelFilepath: String,
    val coverAbsolutePath: String,
    val coverGuess: MovieGuess?,
    val barcodeGuess: MovieGuess?,
    val tmdbResults: List<TmdbSearchResult>,
    val capturedUpc: String?,
)
