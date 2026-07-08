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
                "Season" to "",
                "Number of discs" to "2",
                "TMDB ID" to "603",
                "Force added" to "No",
            ),
            detailFields,
        )
    }
}
