package com.movie.scanner.data.model

data class ReviewItemDetails(
    val featureType: FeatureType,
    val discType: String?,
    val edition: String?,
    val location: String?,
    val seasonNumber: Int?,
    val numberOfDiscs: Int?,
)
