package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieListFormatterTest {
    @Test
    fun buildDetailFields_includesCatalogFieldsAndForceAddedStatus() {
        val movie = MovieEntity(
            id = 7L,
            title = "The Matrix",
            year = "1999",
            tmdbId = 603,
            tmdbUrl = "https://www.themoviedb.org/movie/603",
            posterUrl = "https://image.tmdb.org/poster.jpg",
            upc = "9781234567890",
            isForceAdded = false,
            sortOrder = 1,
            featureType = FeatureType.MOVIE.label,
            discType = "Blu-Ray",
            location = "Shelf A",
            seasonNumber = null,
            numberOfDiscs = 2,
        )

        val detailFields = MovieListFormatter.buildDetailFields(movie)

        assertEquals(
            listOf(
                "Title" to "The Matrix",
                "Year" to "1999",
                "Feature type" to "Movie",
                "Barcode" to "9781234567890",
                "Disc type" to "Blu-Ray",
                "Location" to "Shelf A",
                "Number of discs" to "2",
                "TMDB ID" to "603",
                "Force added" to "No",
            ),
            detailFields,
        )
    }

    @Test
    fun buildDetailFields_includesSeasonForTelevision() {
        val movie = MovieEntity(
            id = 8L,
            title = "Breaking Bad",
            year = "2008",
            tmdbId = 1396,
            tmdbUrl = "https://www.themoviedb.org/tv/1396",
            posterUrl = null,
            upc = null,
            isForceAdded = false,
            sortOrder = 1,
            featureType = FeatureType.TV.label,
            discType = "Blu-Ray",
            location = "Shelf B",
            seasonNumber = 2,
            numberOfDiscs = 4,
        )

        val detailFields = MovieListFormatter.buildDetailFields(movie)

        assertEquals(
            listOf(
                "Title" to "Breaking Bad",
                "Year" to "2008",
                "Feature type" to "TV",
                "Barcode" to "",
                "Disc type" to "Blu-Ray",
                "Location" to "Shelf B",
                "Season" to "2",
                "Number of discs" to "4",
                "TMDB ID" to "1396",
                "Force added" to "No",
            ),
            detailFields,
        )
    }
}
