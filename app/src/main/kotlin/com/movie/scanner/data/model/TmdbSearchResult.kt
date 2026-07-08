package com.movie.scanner.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResult(
    val id: Int,
    val title: String,
    val year: String,
    val posterUrl: String?,
    val tmdbUrl: String,
)
