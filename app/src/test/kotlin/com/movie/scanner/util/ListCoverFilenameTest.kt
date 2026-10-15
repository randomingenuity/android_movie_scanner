package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ListCoverFilenameTest {
    @Test
    fun buildTargetRelFilepath_movieUsesTmdbId() {
        val movie = MovieEntity(
            title = "The Matrix",
            year = "1999",
            tmdbId = 603,
            tmdbUrl = null,
            posterUrl = null,
            upc = null,
            isForceAdded = false,
            sortOrder = 0,
            featureType = FeatureType.MOVIE.label,
        )

        assertEquals("603.jpg", ListCoverFilename.buildTargetRelFilepath(movie, "ignored.jpg"))
    }

    @Test
    fun buildTargetRelFilepath_tvUsesTmdbIdAndSeason() {
        val movie = MovieEntity(
            title = "Breaking Bad",
            year = "2008",
            tmdbId = 1396,
            tmdbUrl = null,
            posterUrl = null,
            upc = null,
            isForceAdded = false,
            sortOrder = 0,
            featureType = FeatureType.TV.label,
            seasonNumber = 1,
        )

        assertEquals("1396_1.jpg", ListCoverFilename.buildTargetRelFilepath(movie, null))
    }

    @Test
    fun buildTargetRelFilepath_manualUsesManualSubdirectory() {
        val movie = MovieEntity(
            title = "Unknown",
            year = "1999",
            tmdbId = null,
            tmdbUrl = null,
            posterUrl = null,
            upc = null,
            isForceAdded = true,
            sortOrder = 0,
        )

        assertEquals(
            "manual/cover_100.jpg",
            ListCoverFilename.buildTargetRelFilepath(movie, "cover_100.jpg"),
        )
    }
}
