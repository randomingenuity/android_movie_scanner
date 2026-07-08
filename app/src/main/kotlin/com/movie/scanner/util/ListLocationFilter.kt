package com.movie.scanner.util

import com.movie.scanner.data.model.MovieEntity

/**
 * Builds location filter choices and applies location filtering on the saved-movie list.
 */
object ListLocationFilter {
    const val ALL_LOCATIONS = "(All)"
    const val UNLOCATED = "UNLOCATED"

    /**
     * One selectable location filter row for the list dropdown.
     */
    data class Option(
        val filterKey: String,
        val menuLabel: String,
    )

    /**
     * Maps a saved movie to its location bucket: blank/null becomes [UNLOCATED].
     */
    fun resolveLocationKey(movie: MovieEntity): String {
        val location = movie.location

        return if (location.isNullOrBlank()) {
            UNLOCATED
        } else {
            location
        }
    }

    /**
     * Builds "(All)" plus one option per distinct location with an item count suffix.
     */
    fun buildOptions(movies: List<MovieEntity>): List<Option> {
        val allOption = Option(
            filterKey = ALL_LOCATIONS,
            menuLabel = ALL_LOCATIONS,
        )

        if (movies.isEmpty()) {
            return listOf(allOption)
        }

        // Group by location bucket, then sort named locations alphabetically with UNLOCATED last.
        val countsByLocation = movies
            .groupingBy { movie -> resolveLocationKey(movie) }
            .eachCount()
        val locationOptions = countsByLocation.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>> { entry -> entry.key == UNLOCATED }
                    .thenBy { entry -> entry.key },
            )
            .map { entry ->
                Option(
                    filterKey = entry.key,
                    menuLabel = "${entry.key} (${entry.value})",
                )
            }

        return listOf(allOption) + locationOptions
    }

    /**
     * Label shown in the location field for the current filter selection.
     */
    fun buildSelectedDisplayLabel(
        selectedFilterKey: String,
        options: List<Option>,
    ): String {
        if (selectedFilterKey == ALL_LOCATIONS) {
            return ALL_LOCATIONS
        }

        return options.firstOrNull { option -> option.filterKey == selectedFilterKey }?.menuLabel
            ?: selectedFilterKey
    }

    /**
     * Returns movies matching [selectedFilterKey]; [ALL_LOCATIONS] keeps the full list.
     */
    fun filterMovies(
        movies: List<MovieEntity>,
        selectedFilterKey: String,
    ): List<MovieEntity> {
        if (selectedFilterKey == ALL_LOCATIONS) {
            return movies
        }

        return movies.filter { movie -> resolveLocationKey(movie) == selectedFilterKey }
    }
}
