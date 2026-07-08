package com.movie.scanner.util

import com.movie.scanner.data.model.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ListLocationFilterTest {
    @Test
    fun buildOptions_includesAllUnlocatedAndNamedLocationsWithCounts() {
        val movies = listOf(
            movie(location = "Shelf B"),
            movie(location = "Shelf A"),
            movie(location = null),
            movie(location = ""),
            movie(location = "Shelf A"),
        )

        val options = ListLocationFilter.buildOptions(movies)

        assertEquals(
            listOf(
                ListLocationFilter.Option(ListLocationFilter.ALL_LOCATIONS, ListLocationFilter.ALL_LOCATIONS),
                ListLocationFilter.Option("Shelf A", "Shelf A (2)"),
                ListLocationFilter.Option("Shelf B", "Shelf B (1)"),
                ListLocationFilter.Option(ListLocationFilter.UNLOCATED, "UNLOCATED (2)"),
            ),
            options,
        )
    }

    @Test
    fun filterMovies_returnsOnlyMatchingLocationBucket() {
        val movies = listOf(
            movie(id = 1L, location = "Shelf A"),
            movie(id = 2L, location = null),
            movie(id = 3L, location = "Shelf B"),
        )

        val filtered = ListLocationFilter.filterMovies(movies, "Shelf A")

        assertEquals(listOf(movies[0]), filtered)
    }

    @Test
    fun filterMovies_allLocations_returnsEveryMovie() {
        val movies = listOf(
            movie(location = "Shelf A"),
            movie(location = null),
        )

        val filtered = ListLocationFilter.filterMovies(movies, ListLocationFilter.ALL_LOCATIONS)

        assertEquals(movies, filtered)
    }

    private fun movie(
        id: Long = 1L,
        location: String?,
    ): MovieEntity = MovieEntity(
        id = id,
        title = "Title $id",
        year = "2000",
        tmdbId = null,
        tmdbUrl = null,
        posterUrl = null,
        upc = null,
        isForceAdded = false,
        sortOrder = 0,
        location = location,
    )
}
