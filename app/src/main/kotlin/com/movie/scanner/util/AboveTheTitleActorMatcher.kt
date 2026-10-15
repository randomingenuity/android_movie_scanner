package com.movie.scanner.util

/**
 * Fuzzy-matches cover OCR star billing against TMDB cast names.
 */
object AboveTheTitleActorMatcher {
    private const val MINIMUM_MATCH_SIMILARITY = 0.82

    data class TmdbCastMemberForMatch(
        val tmdbPersonId: Int,
        val name: String,
    )

    data class MatchResult(
        val matchedTmdbNames: List<String>,
        val matchedTmdbPersonIds: List<Int>,
        val unmatchedNormalizedNames: List<String>,
    )

    fun parseCommaSeparatedActorNames(raw: String): List<String> =
        raw.split(",")
            .map { segment -> segment.trim() }
            .filter { segment -> segment.isNotBlank() }

    fun joinCommaSeparated(names: List<String>): String =
        names.joinToString(", ")

    fun joinCommaSeparatedTmdbPersonIds(personIds: List<Int>): String =
        personIds.joinToString(", ")

    /**
     * Matches scanned billing names to TMDB cast in scan order; unmatched names are title-cased.
     */
    fun matchScannedNamesToTmdbCast(
        scannedNames: List<String>,
        tmdbCastMembers: List<TmdbCastMemberForMatch>,
    ): MatchResult {
        if (scannedNames.isEmpty()) {
            return MatchResult(emptyList(), emptyList(), emptyList())
        }
        val availableCast = tmdbCastMembers.toMutableList()
        val matchedTmdbNames = mutableListOf<String>()
        val matchedTmdbPersonIds = mutableListOf<Int>()
        val unmatchedNormalizedNames = mutableListOf<String>()

        for (scannedName in scannedNames) {
            val bestCastIndex = findBestCastMatchIndex(scannedName, availableCast.map { member -> member.name })
            if (bestCastIndex != null) {
                val matchedMember = availableCast.removeAt(bestCastIndex)
                matchedTmdbNames.add(matchedMember.name)
                matchedTmdbPersonIds.add(matchedMember.tmdbPersonId)
            } else {
                unmatchedNormalizedNames.add(normalizeActorDisplayName(scannedName))
            }
        }

        return MatchResult(
            matchedTmdbNames = matchedTmdbNames,
            matchedTmdbPersonIds = matchedTmdbPersonIds,
            unmatchedNormalizedNames = unmatchedNormalizedNames,
        )
    }

    fun normalizeActorDisplayName(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter { token -> token.isNotBlank() }
            .joinToString(" ") { token ->
                token.lowercase().replaceFirstChar { character -> character.titlecase() }
            }

    private fun findBestCastMatchIndex(
        scannedName: String,
        castNames: List<String>,
    ): Int? {
        var bestIndex: Int? = null
        var bestSimilarity = MINIMUM_MATCH_SIMILARITY
        val normalizedScanned = normalizeNameForComparison(scannedName)
        if (normalizedScanned.isEmpty()) {
            return null
        }
        for (index in castNames.indices) {
            val castName = castNames[index]
            val similarity = computeNameSimilarity(normalizedScanned, normalizeNameForComparison(castName))
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun computeNameSimilarity(normalizedScanned: String, normalizedCast: String): Double {
        if (normalizedScanned == normalizedCast) {
            return 1.0
        }
        if (normalizedCast.contains(normalizedScanned) || normalizedScanned.contains(normalizedCast)) {
            val shorterLength = minOf(normalizedScanned.length, normalizedCast.length)
            val longerLength = maxOf(normalizedScanned.length, normalizedCast.length)
            if (shorterLength >= 3 && shorterLength.toDouble() / longerLength >= 0.5) {
                return 0.95
            }
        }
        val scannedLastToken = normalizedScanned.substringAfterLast(' ').takeIf { token -> token.isNotBlank() }
        val castLastToken = normalizedCast.substringAfterLast(' ').takeIf { token -> token.isNotBlank() }
        val fullRatio = levenshteinSimilarity(normalizedScanned, normalizedCast)
        if (scannedLastToken != null && castLastToken != null && scannedLastToken.length >= 3) {
            val lastTokenRatio = levenshteinSimilarity(scannedLastToken, castLastToken)
            return maxOf(fullRatio, lastTokenRatio * 0.98)
        }
        return fullRatio
    }

    private fun normalizeNameForComparison(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levenshteinSimilarity(left: String, right: String): Double {
        if (left == right) {
            return 1.0
        }
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) {
            return 1.0
        }
        val distance = computeLevenshteinDistance(left, right)

        return 1.0 - distance.toDouble() / maxLength
    }

    private fun computeLevenshteinDistance(left: String, right: String): Int {
        val rowCount = left.length + 1
        val columnCount = right.length + 1
        val distances = IntArray(columnCount) { columnIndex -> columnIndex }
        for (rowIndex in 1 until rowCount) {
            var previousDiagonal = distances[0]
            distances[0] = rowIndex
            for (columnIndex in 1 until columnCount) {
                val temporary = distances[columnIndex]
                val substitutionCost = if (left[rowIndex - 1] == right[columnIndex - 1]) {
                    0
                } else {
                    1
                }
                distances[columnIndex] = minOf(
                    distances[columnIndex] + 1,
                    distances[columnIndex - 1] + 1,
                    previousDiagonal + substitutionCost,
                )
                previousDiagonal = temporary
            }
        }
        return distances[columnCount - 1]
    }
}
