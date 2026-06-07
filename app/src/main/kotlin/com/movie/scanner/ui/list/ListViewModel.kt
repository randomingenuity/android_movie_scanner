package com.movie.scanner.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
) : ViewModel() {
    val movies: StateFlow<List<MovieEntity>> = movieRepository.observeMovies()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

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
