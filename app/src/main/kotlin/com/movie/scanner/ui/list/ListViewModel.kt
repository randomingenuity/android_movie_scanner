package com.movie.scanner.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.util.ListLocationFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Saved-movie list state: full catalog, location filter selection, and filtered rows.
 */
data class ListUiState(
    val allMovies: List<MovieEntity> = emptyList(),
    val isLoadingMovies: Boolean = true,
    val selectedLocationFilter: String = ListLocationFilter.ALL_LOCATIONS,
    val locationFilterOptions: List<ListLocationFilter.Option> = emptyList(),
    val displayedMovies: List<MovieEntity> = emptyList(),
)

@HiltViewModel
class ListViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
) : ViewModel() {
    private val selectedLocationFilter = MutableStateFlow(ListLocationFilter.ALL_LOCATIONS)

    val uiState: StateFlow<ListUiState> = combine(
        movieRepository.observeMovies(),
        selectedLocationFilter,
    ) { movies, locationFilter ->
        val locationFilterOptions = ListLocationFilter.buildOptions(movies)
        val activeLocationFilter = if (
            locationFilter != ListLocationFilter.ALL_LOCATIONS &&
            locationFilterOptions.none { option -> option.filterKey == locationFilter }
        ) {
            ListLocationFilter.ALL_LOCATIONS
        } else {
            locationFilter
        }

        ListUiState(
            allMovies = movies,
            isLoadingMovies = false,
            selectedLocationFilter = activeLocationFilter,
            locationFilterOptions = locationFilterOptions,
            displayedMovies = ListLocationFilter.filterMovies(movies, activeLocationFilter),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    /**
     * Updates the location filter shown at the top of the list screen.
     */
    fun selectLocationFilter(locationFilter: String) {
        selectedLocationFilter.value = locationFilter
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
