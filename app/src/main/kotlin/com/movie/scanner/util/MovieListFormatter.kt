package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity

object MovieListFormatter {
    /**
     * Label/value pairs for the list detail overlay (catalog fields and force-added status; URLs omitted).
     */
    fun buildDetailFields(movie: MovieEntity): List<Pair<String, String>> {
        val featureType = FeatureType.fromLabel(movie.featureType)
        return listOf(
            "Title" to movie.title,
            "Year" to movie.year,
            "Feature type" to movie.featureType,
            "Barcode" to movie.upc.orEmpty(),
            "Disc type" to movie.discType.orEmpty(),
            "Location" to movie.location.orEmpty(),
            "Season" to if (featureType == FeatureType.TV) {
                movie.seasonNumber?.toString().orEmpty()
            } else {
                ""
            },
            "Number of discs" to movie.numberOfDiscs?.toString().orEmpty(),
            "TMDB ID" to movie.tmdbId?.toString().orEmpty(),
            "Force added" to if (movie.isForceAdded) "Yes" else "No",
        )
    }

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
