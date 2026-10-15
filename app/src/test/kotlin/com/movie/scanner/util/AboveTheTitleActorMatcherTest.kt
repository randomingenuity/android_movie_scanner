package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AboveTheTitleActorMatcherTest {
    @Test
    fun matchScannedNamesToTmdbCast_usesTmdbNameForFuzzyMatch() {
        val result = AboveTheTitleActorMatcher.matchScannedNamesToTmdbCast(
            scannedNames = listOf("Al Paccino", "Tom Hanks"),
            tmdbCastNames = listOf("Robert De Niro", "Al Pacino", "Tom Hanks"),
        )

        assertEquals(listOf("Al Pacino", "Tom Hanks"), result.matchedTmdbNames)
        assertEquals(emptyList<String>(), result.unmatchedNormalizedNames)
    }

    @Test
    fun matchScannedNamesToTmdbCast_putsUnknownNamesInUnmatchedWithTitleCase() {
        val result = AboveTheTitleActorMatcher.matchScannedNamesToTmdbCast(
            scannedNames = listOf("UNKNOWN STAR", "Tom Hanks"),
            tmdbCastNames = listOf("Tom Hanks"),
        )

        assertEquals(listOf("Tom Hanks"), result.matchedTmdbNames)
        assertEquals(listOf("Unknown Star"), result.unmatchedNormalizedNames)
    }

    @Test
    fun buildFromRawBilling_splitsMatchedAndUnmatchedColumns() {
        val resolution = AboveTheTitleActorResolution.buildFromRawBilling(
            rawAboveTheTitle = "Al Paccino, UNKNOWN STAR",
            tmdbCastNames = listOf("Al Pacino"),
        )

        assertEquals("Al Pacino", resolution.matchedCommaSeparated)
        assertEquals("Unknown Star", resolution.unmatchedCommaSeparated)
    }
}
