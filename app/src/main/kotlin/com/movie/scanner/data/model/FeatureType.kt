package com.movie.scanner.data.model

enum class FeatureType(val label: String) {
    MOVIE("Movie"),
    TV("TV"),
    ;

    companion object {
        fun fromLabel(label: String): FeatureType =
            entries.firstOrNull { featureType -> featureType.label == label } ?: MOVIE
    }
}
