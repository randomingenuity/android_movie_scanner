package com.movie.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieEditionTest {
    @Test
    fun options_areSortedByLabel() {
        assertEquals(
            listOf(
                "Deluxe Edition",
                "Director's Cut",
                "Extended Version",
                "No specific edition",
                "Restored",
                "Special Edition",
                "Steelbox Edition",
                "Theatrical",
                "Unrated",
                "With Bonus Material",
            ),
            MovieEdition.options.map { edition -> edition.label },
        )
    }

    @Test
    fun labelForStored_returnsLabelForCanonicalValue() {
        assertEquals("Theatrical", MovieEdition.labelForStored("theatrical"))
        assertEquals("Director's Cut", MovieEdition.labelForStored("directors_cut"))
        assertEquals("No specific edition", MovieEdition.labelForStored(""))
    }

    @Test
    fun fromStored_resolvesEmptyStringAsNone() {
        assertEquals(MovieEdition.NO_SPECIFIC_EDITION, MovieEdition.fromStored(""))
        assertEquals(MovieEdition.THEATRICAL, MovieEdition.fromStored("theatrical"))
        assertNull(MovieEdition.fromStored("unknown"))
    }
}
