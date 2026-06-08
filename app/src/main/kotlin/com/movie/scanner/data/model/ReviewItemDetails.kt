package com.movie.scanner.data.model

data class ReviewItemDetails(
    val featureType: FeatureType,
    val discType: String?,
    val location: String?,
    val seasonNumber: Int?,
    val numberOfDiscs: Int?,
)
