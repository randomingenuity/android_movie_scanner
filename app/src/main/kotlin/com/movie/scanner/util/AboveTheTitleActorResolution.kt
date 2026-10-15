package com.movie.scanner.util

/**
 * Resolved above-the-title billing for persistence and CSV export.
 */
data class AboveTheTitleActorResolution(
    val matchedNamesCommaSeparated: String?,
    val matchedTmdbPersonIdsCommaSeparated: String?,
    val unmatchedCommaSeparated: String?,
) {
    companion object {
        fun buildFromRawBilling(
            rawAboveTheTitle: String,
            tmdbCastMembers: List<AboveTheTitleActorMatcher.TmdbCastMemberForMatch>,
        ): AboveTheTitleActorResolution {
            val scannedNames = AboveTheTitleActorMatcher.parseCommaSeparatedActorNames(rawAboveTheTitle)
            if (scannedNames.isEmpty()) {
                return AboveTheTitleActorResolution(null, null, null)
            }
            val matchResult = AboveTheTitleActorMatcher.matchScannedNamesToTmdbCast(
                scannedNames = scannedNames,
                tmdbCastMembers = tmdbCastMembers,
            )
            val matchedNamesCommaSeparated =
                AboveTheTitleActorMatcher.joinCommaSeparated(matchResult.matchedTmdbNames)
                    .takeIf { value -> value.isNotBlank() }
            val matchedTmdbPersonIdsCommaSeparated =
                AboveTheTitleActorMatcher.joinCommaSeparatedTmdbPersonIds(matchResult.matchedTmdbPersonIds)
                    .takeIf { value -> value.isNotBlank() }
            val unmatchedCommaSeparated =
                AboveTheTitleActorMatcher.joinCommaSeparated(matchResult.unmatchedNormalizedNames)
                    .takeIf { value -> value.isNotBlank() }

            return AboveTheTitleActorResolution(
                matchedNamesCommaSeparated = matchedNamesCommaSeparated,
                matchedTmdbPersonIdsCommaSeparated = matchedTmdbPersonIdsCommaSeparated,
                unmatchedCommaSeparated = unmatchedCommaSeparated,
            )
        }

        fun buildUnmatchedOnlyFromRawBilling(rawAboveTheTitle: String): AboveTheTitleActorResolution {
            val scannedNames = AboveTheTitleActorMatcher.parseCommaSeparatedActorNames(rawAboveTheTitle)
            if (scannedNames.isEmpty()) {
                return AboveTheTitleActorResolution(null, null, null)
            }
            val normalizedNames = scannedNames.map { name ->
                AboveTheTitleActorMatcher.normalizeActorDisplayName(name)
            }
            val unmatchedCommaSeparated = AboveTheTitleActorMatcher.joinCommaSeparated(normalizedNames)
                .takeIf { value -> value.isNotBlank() }

            return AboveTheTitleActorResolution(
                matchedNamesCommaSeparated = null,
                matchedTmdbPersonIdsCommaSeparated = null,
                unmatchedCommaSeparated = unmatchedCommaSeparated,
            )
        }
    }
}
