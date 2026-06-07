package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MovieGuessJsonTest {
    @Test
    fun parse_readsPlainJsonObject() {
        val guess = MovieGuessJson.parse(
            """
            {
              "title": "The Matrix",
              "year": "1999",
              "confidence": 0.95
            }
            """.trimIndent(),
        )

        assertEquals("The Matrix", guess.title)
        assertEquals("1999", guess.year)
        assertEquals(0.95, guess.confidence!!, 0.0001)
    }

    @Test
    fun parse_stripsMarkdownFence() {
        val guess = MovieGuessJson.parse(
            """
            ```json
            {
              "title": "Arrival",
              "year": "2016"
            }
            ```
            """.trimIndent(),
        )

        assertEquals("Arrival", guess.title)
        assertEquals("2016", guess.year)
    }
}
