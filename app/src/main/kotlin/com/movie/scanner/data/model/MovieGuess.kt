package com.movie.scanner.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MovieGuess(
    val title: String = "",
    val year: String = "",
    val confidence: Double? = null,
    /** Comma-separated actor names from star billing above the title on the cover. */
    val aboveTheTitle: String = "",
)
