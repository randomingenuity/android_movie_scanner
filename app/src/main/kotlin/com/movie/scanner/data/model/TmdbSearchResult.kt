package com.movie.scanner.data.model

data class TmdbSearchResult(
    val id: Int,
    val title: String,
    val year: String,
    val posterUrl: String?,
    val tmdbUrl: String,
)
