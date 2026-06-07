package com.movie.scanner.util

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
            ),
        )

        val csv = CsvExporter.buildCsv(movies)
        val lines = csv.lines()

        assertEquals(
            "title,year,barcode,tmdb_url,tmdb_id,poster_url",
            lines[0],
        )
        assertEquals(
            "\"The \"\"Best\"\" Movie\",\"2020\",\"012345678905\",\"https://www.themoviedb.org/movie/42\",\"42\",\"https://image.tmdb.org/poster.jpg\"",
            lines[1],
        )
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

        assertTrue(csv.endsWith("\"Force Added\",\"1999\",\"\",\"\",\"\",\"\""))
    }
}
