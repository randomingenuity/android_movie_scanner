package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    @Test
    fun buildDefaultFilename_usesTimestampPattern() {
        val filename = CsvExporter.buildDefaultFilename()

        assertTrue(filename.matches(Regex("""\d{8}-\d{4}_movies\.csv""")))
    }

    @Test
    fun buildCsv_writesHeaderAndQuotedFields() {
        val movies = listOf(
            MovieEntity(
                title = "The \"Best\" Movie",
                year = "2020",
                tmdbId = 42,
                tmdbUrl = "https://www.themoviedb.org/movie/42",
                posterUrl = "https://image.tmdb.org/poster.jpg",
                upc = "012345678905",
                isForceAdded = false,
                sortOrder = 0,
                featureType = FeatureType.MOVIE.label,
                discType = "Blu-Ray",
                location = "Shelf A",
            ),
        )

        val csv = CsvExporter.buildCsv(movies)
        val lines = csv.lines()

        assertEquals(
            "title,year,feature_type,barcode,disc_type,location,season_number,number_of_discs,tmdb_url,tmdb_id,poster_url",
            lines[0],
        )
        assertEquals(
            "\"The \"\"Best\"\" Movie\",\"2020\",\"Movie\",\"012345678905\",\"Blu-Ray\",\"Shelf A\",\"\",\"\",\"https://www.themoviedb.org/movie/42\",\"42\",\"https://image.tmdb.org/poster.jpg\"",
            lines[1],
        )
    }

    @Test
    fun buildCsv_includesNumberOfDiscsForMovie() {
        val movies = listOf(
            MovieEntity(
                title = "The Matrix",
                year = "1999",
                tmdbId = 603,
                tmdbUrl = "https://www.themoviedb.org/movie/603",
                posterUrl = null,
                upc = null,
                isForceAdded = false,
                sortOrder = 0,
                featureType = FeatureType.MOVIE.label,
                discType = "Blu-Ray",
                numberOfDiscs = 2,
            ),
        )

        val csv = CsvExporter.buildCsv(movies)

        assertTrue(csv.contains("\"The Matrix\",\"1999\",\"Movie\",\"\",\"Blu-Ray\",\"\",\"\",\"2\","))
    }

    @Test
    fun buildCsv_includesTvFieldsWhenModeIsTv() {
        val movies = listOf(
            MovieEntity(
                title = "Breaking Bad",
                year = "2008",
                tmdbId = 1396,
                tmdbUrl = "https://www.themoviedb.org/tv/1396",
                posterUrl = null,
                upc = null,
                isForceAdded = false,
                sortOrder = 0,
                featureType = FeatureType.TV.label,
                discType = "DVD",
                seasonNumber = 1,
                numberOfDiscs = 4,
            ),
        )

        val csv = CsvExporter.buildCsv(movies)

        assertTrue(csv.contains("\"Breaking Bad\",\"2008\",\"TV\",\"\",\"DVD\",\"\",\"1\",\"4\","))
    }

    @Test
    fun buildCsv_leavesEmptyOptionalFieldsQuoted() {
        val movies = listOf(
            MovieEntity(
                title = "Force Added",
                year = "1999",
                tmdbId = null,
                tmdbUrl = null,
                posterUrl = null,
                upc = null,
                isForceAdded = true,
                sortOrder = 1,
            ),
        )

        val csv = CsvExporter.buildCsv(movies)

        assertTrue(
            csv.endsWith(
                "\"Force Added\",\"1999\",\"Movie\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"",
            ),
        )
    }
}
