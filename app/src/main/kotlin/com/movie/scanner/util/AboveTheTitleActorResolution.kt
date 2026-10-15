package com.movie.scanner.util

/**
 * Resolved above-the-title billing for persistence and CSV export.
 */
data class AboveTheTitleActorResolution(
    val matchedCommaSeparated: String?,
    val unmatchedCommaSeparated: String?,
) {
    companion object {
        fun buildFromRawBilling(
            rawAboveTheTitle: String,
            tmdbCastNames: List<String>,
        ): AboveTheTitleActorResolution {
            val scannedNames = AboveTheTitleActorMatcher.parseCommaSeparatedActorNames(rawAboveTheTitle)
            if (scannedNames.isEmpty()) {
                return AboveTheTitleActorResolution(null, null)
            }
            val matchResult = AboveTheTitleActorMatcher.matchScannedNamesToTmdbCast(
                scannedNames = scannedNames,
                tmdbCastNames = tmdbCastNames,
            )
            val matchedCommaSeparated = AboveTheTitleActorMatcher.joinCommaSeparated(matchResult.matchedTmdbNames)
                .takeIf { value -> value.isNotBlank() }
            val unmatchedCommaSeparated =
                AboveTheTitleActorMatcher.joinCommaSeparated(matchResult.unmatchedNormalizedNames)
                    .takeIf { value -> value.isNotBlank() }

            return AboveTheTitleActorResolution(
                matchedCommaSeparated = matchedCommaSeparated,
                unmatchedCommaSeparated = unmatchedCommaSeparated,
            )
        }

        fun buildUnmatchedOnlyFromRawBilling(rawAboveTheTitle: String): AboveTheTitleActorResolution {
            val scannedNames = AboveTheTitleActorMatcher.parseCommaSeparatedActorNames(rawAboveTheTitle)
            if (scannedNames.isEmpty()) {
                return AboveTheTitleActorResolution(null, null)
            }
            val normalizedNames = scannedNames.map { name ->
                AboveTheTitleActorMatcher.normalizeActorDisplayName(name)
            }
            val unmatchedCommaSeparated = AboveTheTitleActorMatcher.joinCommaSeparated(normalizedNames)
                .takeIf { value -> value.isNotBlank() }

            return AboveTheTitleActorResolution(
                matchedCommaSeparated = null,
                unmatchedCommaSeparated = unmatchedCommaSeparated,
            )
        }
    }
}
