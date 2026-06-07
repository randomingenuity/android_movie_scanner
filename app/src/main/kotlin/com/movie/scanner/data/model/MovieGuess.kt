package com.movie.scanner.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MovieGuess(
    val title: String = "",
    val year: String = "",
    val confidence: Double? = null,
)
