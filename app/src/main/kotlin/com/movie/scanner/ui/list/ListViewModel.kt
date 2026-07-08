package com.movie.scanner.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.util.ListLocationFilter
import com.movie.scanner.util.ListPagination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Saved-movie list state: full catalog, location filter selection, paged rows, and paging metadata.
 */
data class ListUiState(
    val allMovies: List<MovieEntity> = emptyList(),
    val isLoadingMovies: Boolean = true,
    val selectedLocationFilter: String = ListLocationFilter.ALL_LOCATIONS,
    val locationFilterOptions: List<ListLocationFilter.Option> = emptyList(),
    val displayedMovies: List<MovieEntity> = emptyList(),
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val totalFilteredCount: Int = 0,
    val pageRangeLabel: String = "",
    val hasPreviousPage: Boolean = false,
    val hasNextPage: Boolean = false,
)

@HiltViewModel
class ListViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
) : ViewModel() {
    private val selectedLocationFilter = MutableStateFlow(ListLocationFilter.ALL_LOCATIONS)
    private val currentPageIndex = MutableStateFlow(0)

    val uiState: StateFlow<ListUiState> = combine(
        movieRepository.observeMovies(),
        selectedLocationFilter,
        currentPageIndex,
    ) { movies, locationFilter, pageIndex ->
        val locationFilterOptions = ListLocationFilter.buildOptions(movies)
        val activeLocationFilter = if (
            locationFilter != ListLocationFilter.ALL_LOCATIONS &&
            locationFilterOptions.none { option -> option.filterKey == locationFilter }
        ) {
            ListLocationFilter.ALL_LOCATIONS
        } else {
            locationFilter
        }

        val filteredMovies = ListLocationFilter.filterMovies(movies, activeLocationFilter)
        val page = ListPagination.paginate(filteredMovies, pageIndex)

        ListUiState(
            allMovies = movies,
            isLoadingMovies = false,
            selectedLocationFilter = activeLocationFilter,
            locationFilterOptions = locationFilterOptions,
            displayedMovies = page.items,
            currentPageIndex = page.currentPageIndex,
            totalPages = page.totalPages,
            totalFilteredCount = page.totalItemCount,
            pageRangeLabel = ListPagination.buildPageRangeLabel(page),
            hasPreviousPage = page.hasPreviousPage,
            hasNextPage = page.hasNextPage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (state.totalPages > 0 && currentPageIndex.value >= state.totalPages) {
                    currentPageIndex.value = state.totalPages - 1
                }
            }
        }
    }

    /**
     * Updates the location filter shown at the top of the list screen.
     */
    fun selectLocationFilter(locationFilter: String) {
        selectedLocationFilter.value = locationFilter
        currentPageIndex.value = 0
    }

    /**
     * Shows the previous page of filtered movies when one exists.
     */
    fun showPreviousPage() {
        if (uiState.value.hasPreviousPage) {
            currentPageIndex.value = uiState.value.currentPageIndex - 1
        }
    }

    /**
     * Shows the next page of filtered movies when one exists.
     */
    fun showNextPage() {
        if (uiState.value.hasNextPage) {
            currentPageIndex.value = uiState.value.currentPageIndex + 1
        }
    }

    fun deleteMovie(movieId: Long) {
        viewModelScope.launch {
            movieRepository.deleteMovie(movieId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            movieRepository.clearAll()
        }
    }

    suspend fun loadMoviesForExport(): List<MovieEntity> = movieRepository.listMovies()
}
