package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import java.io.File

/**
 * Relative paths and CSV basenames for catalog cover files under the list covers directory.
 */
object ListCoverFilename {
    fun buildTargetRelFilepath(movie: MovieEntity, manualCaptureBasename: String?): String? {
        val tmdbId = movie.tmdbId
        if (tmdbId != null) {
            if (FeatureType.fromLabel(movie.featureType) == FeatureType.TV) {
                val seasonNumber = movie.seasonNumber ?: return null
                return "${tmdbId}_${seasonNumber}.jpg"
            }
            return "${tmdbId}.jpg"
        }
        if (manualCaptureBasename.isNullOrBlank()) {
            return null
        }
        return "manual/$manualCaptureBasename"
    }

    fun buildCoverFilenameForCsv(coverRelFilepath: String?): String {
        if (coverRelFilepath.isNullOrBlank()) {
            return ""
        }
        return File(coverRelFilepath).name
    }

    fun buildZipEntryPath(coverRelFilepath: String): String = "covers/$coverRelFilepath"
}
