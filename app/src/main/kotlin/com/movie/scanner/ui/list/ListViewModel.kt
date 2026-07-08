package com.movie.scanner.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val movies: List<MovieEntity> = emptyList(),
    val isLoadingMovies: Boolean = true,
)

@HiltViewModel
class ListViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            movieRepository.observeMovies().collect { movies ->
                _uiState.update { state ->
                    state.copy(
                        movies = movies,
                        isLoadingMovies = false,
                    )
                }
            }
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
