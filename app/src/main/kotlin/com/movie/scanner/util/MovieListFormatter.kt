package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity

object MovieListFormatter {
    fun buildMetadataLines(movie: MovieEntity): List<String> {
        val lines = mutableListOf<String>()
        lines.add("Feature type: ${movie.featureType}")
        movie.discType?.takeIf { discType -> discType.isNotBlank() }?.let { discType ->
            lines.add("Disc: $discType")
        }
        movie.location?.takeIf { location -> location.isNotBlank() }?.let { location ->
            lines.add("Location: $location")
        }
        if (FeatureType.fromLabel(movie.featureType) == FeatureType.TV) {
            movie.seasonNumber?.let { seasonNumber ->
                lines.add("Season: $seasonNumber")
            }
        }
        movie.numberOfDiscs?.let { numberOfDiscs ->
            lines.add("Discs: $numberOfDiscs")
        }
        return lines
    }
}
