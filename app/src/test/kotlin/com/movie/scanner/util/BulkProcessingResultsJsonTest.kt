package com.movie.scanner.util

import com.movie.scanner.data.model.BulkProcessingResults
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BulkProcessingResultsJsonTest {
    @Test
    fun encodeAndParse_roundTripsRecognitionPayload() {
        val results = BulkProcessingResults(
            coverGuess = MovieGuess(title = "Arrival", year = "2016", confidence = 0.9),
            barcodeGuess = MovieGuess(title = "Arrival", year = "2016"),
            tmdbResults = listOf(
                TmdbSearchResult(
                    id = 42,
                    title = "Arrival",
                    year = "2016",
                    posterUrl = "https://example.com/poster.jpg",
                    tmdbUrl = "https://www.themoviedb.org/movie/42",
                ),
            ),
            capturedUpc = "9781234567890",
            recognitionError = null,
        )

        val parsed = BulkProcessingResultsJson.parse(BulkProcessingResultsJson.encode(results))

        assertEquals("Arrival", parsed.coverGuess?.title)
        assertEquals("2016", parsed.coverGuess?.year)
        assertEquals("9781234567890", parsed.capturedUpc)
        assertEquals(1, parsed.tmdbResults.size)
        assertEquals(42, parsed.tmdbResults.first().id)
        assertNull(parsed.recognitionError)
    }
}
