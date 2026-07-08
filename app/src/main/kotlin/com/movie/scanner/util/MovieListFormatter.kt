package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity

object MovieListFormatter {
    /**
     * Label/value pairs for the list detail overlay (catalog fields and force-added status; URLs omitted).
     * Type-specific fields match the review form: Season appears only for TV.
     */
    fun buildDetailFields(movie: MovieEntity): List<Pair<String, String>> {
        val featureType = FeatureType.fromLabel(movie.featureType)
        val detailFields = mutableListOf(
            "Title" to movie.title,
            "Year" to movie.year,
            "Feature type" to movie.featureType,
            "Barcode" to movie.upc.orEmpty(),
            "Disc type" to movie.discType.orEmpty(),
            "Location" to movie.location.orEmpty(),
        )

        // TV-only fields (same visibility rules as the review form).
        if (featureType == FeatureType.TV) {
            detailFields.add("Season" to movie.seasonNumber?.toString().orEmpty())
        }

        detailFields.add("Number of discs" to movie.numberOfDiscs?.toString().orEmpty())
        detailFields.add("TMDB ID" to movie.tmdbId?.toString().orEmpty())
        detailFields.add("Force added" to if (movie.isForceAdded) "Yes" else "No")

        return detailFields
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
