package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AboveTheTitleActorMatcherTest {
    @Test
    fun matchScannedNamesToTmdbCast_usesTmdbNameForFuzzyMatch() {
        val result = AboveTheTitleActorMatcher.matchScannedNamesToTmdbCast(
            scannedNames = listOf("Al Paccino", "Tom Hanks"),
            tmdbCastMembers = listOf(
                AboveTheTitleActorMatcher.TmdbCastMemberForMatch(1, "Robert De Niro"),
                AboveTheTitleActorMatcher.TmdbCastMemberForMatch(2, "Al Pacino"),
                AboveTheTitleActorMatcher.TmdbCastMemberForMatch(3, "Tom Hanks"),
            ),
        )

        assertEquals(listOf("Al Pacino", "Tom Hanks"), result.matchedTmdbNames)
        assertEquals(listOf(2, 3), result.matchedTmdbPersonIds)
        assertEquals(emptyList<String>(), result.unmatchedNormalizedNames)
    }

    @Test
    fun matchScannedNamesToTmdbCast_putsUnknownNamesInUnmatchedWithTitleCase() {
        val result = AboveTheTitleActorMatcher.matchScannedNamesToTmdbCast(
            scannedNames = listOf("UNKNOWN STAR", "Tom Hanks"),
            tmdbCastMembers = listOf(
                AboveTheTitleActorMatcher.TmdbCastMemberForMatch(31, "Tom Hanks"),
            ),
        )

        assertEquals(listOf("Tom Hanks"), result.matchedTmdbNames)
        assertEquals(listOf(31), result.matchedTmdbPersonIds)
        assertEquals(listOf("Unknown Star"), result.unmatchedNormalizedNames)
    }

    @Test
    fun buildFromRawBilling_splitsMatchedAndUnmatchedColumns() {
        val resolution = AboveTheTitleActorResolution.buildFromRawBilling(
            rawAboveTheTitle = "Al Paccino, UNKNOWN STAR",
            tmdbCastMembers = listOf(
                AboveTheTitleActorMatcher.TmdbCastMemberForMatch(1158, "Al Pacino"),
            ),
        )

        assertEquals("Al Pacino", resolution.matchedNamesCommaSeparated)
        assertEquals("1158", resolution.matchedTmdbPersonIdsCommaSeparated)
        assertEquals("Unknown Star", resolution.unmatchedCommaSeparated)
    }
}
