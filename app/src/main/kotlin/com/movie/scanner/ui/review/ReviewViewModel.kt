package com.movie.scanner.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.data.repository.TmdbRepository
import com.movie.scanner.data.session.ScanSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val title: String = "",
    val year: String = "",
    val barcode: String = "",
    val extractedCoverTitle: String = "",
    val barcodeUsageMessage: String? = null,
    val barcodeLlmMessage: String = "",
    val barcodeSuggestion: MovieGuess? = null,
    val tmdbResults: List<TmdbSearchResult> = emptyList(),
    val selectedTmdbResult: TmdbSearchResult? = null,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val duplicateMessage: String? = null,
    val actionMessage: String? = null,
    val isAddEnabled: Boolean = false,
    val showReplaceAdd: Boolean = false,
    val showForceAdd: Boolean = false,
    val isForceAddEnabled: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val finished: Boolean = false,
    val addedTitle: String? = null,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val scanSessionHolder: ScanSessionHolder,
    private val tmdbRepository: TmdbRepository,
    private val movieRepository: MovieRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        val coverGuess = scanSessionHolder.coverGuess
        val barcodeGuess = scanSessionHolder.barcodeGuess
        val title = coverGuess?.title?.takeIf { title -> title.isNotBlank() }
            ?: barcodeGuess?.title.orEmpty()
        val year = coverGuess?.year?.takeIf { year -> year.isNotBlank() }
            ?: barcodeGuess?.year.orEmpty()
        val barcodeSuggestion = barcodeGuess?.takeIf {
            it.title.isNotBlank() &&
                (it.title != title || it.year != year)
        }
        val initialResults = scanSessionHolder.initialTmdbResults
        val capturedBarcode = scanSessionHolder.resolveCapturedUpc()
        _uiState.value = ReviewUiState(
            title = title,
            year = year,
            barcode = removeNewlinesFromBarcode(capturedBarcode.orEmpty()),
            extractedCoverTitle = coverGuess?.title?.trim().orEmpty(),
            barcodeUsageMessage = buildBarcodeUsageMessage(
                upc = capturedBarcode,
                coverGuess = coverGuess,
                barcodeGuess = barcodeGuess,
            ),
            barcodeLlmMessage = buildBarcodeLlmMessage(
                upc = capturedBarcode,
                barcodeGuess = barcodeGuess,
            ),
            barcodeSuggestion = barcodeSuggestion,
            tmdbResults = initialResults,
            selectedTmdbResult = initialResults.firstOrNull(),
        )
        viewModelScope.launch { refreshActionState() }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, duplicateMessage = null, actionMessage = null) }
        viewModelScope.launch { refreshActionState() }
    }

    fun updateYear(value: String) {
        _uiState.update { it.copy(year = value, duplicateMessage = null, actionMessage = null) }
        viewModelScope.launch { refreshActionState() }
    }

    fun updateBarcode(value: String): String {
        val sanitized = removeNewlinesFromBarcode(value)
        _uiState.update { it.copy(barcode = sanitized, duplicateMessage = null, actionMessage = null) }
        return sanitized
    }

    fun applyBarcodeSuggestion() {
        val suggestion = _uiState.value.barcodeSuggestion ?: return
        _uiState.update {
            it.copy(
                title = suggestion.title,
                year = suggestion.year,
                duplicateMessage = null,
                actionMessage = null,
            )
        }
        viewModelScope.launch { refreshActionState() }
    }

    fun selectTmdbResult(result: TmdbSearchResult) {
        _uiState.update {
            it.copy(
                selectedTmdbResult = result,
                title = result.title,
                year = result.year.ifBlank { it.year },
                duplicateMessage = null,
                actionMessage = null,
            )
        }
        viewModelScope.launch { refreshActionState() }
    }

    fun searchTmdb() {
        val title = _uiState.value.title.trim()
        if (title.isEmpty()) {
            return
        }
        val year = _uiState.value.year.trim().ifBlank { null }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            val result = tmdbRepository.searchMovies(title, year)
            _uiState.update {
                if (result.isSuccess) {
                    val results = result.getOrDefault(emptyList())
                    it.copy(
                        isSearching = false,
                        tmdbResults = results,
                        selectedTmdbResult = results.firstOrNull(),
                        searchError = if (results.isEmpty()) "No matches found." else null,
                    )
                } else {
                    it.copy(
                        isSearching = false,
                        searchError = result.exceptionOrNull()?.message ?: "TMDB search failed.",
                    )
                }
            }
            refreshActionState()
        }
    }

    fun addMovie() {
        val state = _uiState.value
        val selected = state.selectedTmdbResult ?: return
        val capturedBarcode = resolveCapturedBarcode(state)
        viewModelScope.launch {
            val result = movieRepository.addMatchedMovie(
                title = state.title.trim(),
                year = state.year.trim(),
                upc = capturedBarcode,
                match = selected,
            )
            if (result.isSuccess) {
                finishAfterAdd(state.title)
            } else {
                _uiState.update {
                    it.copy(actionMessage = result.exceptionOrNull()?.message ?: "Could not add movie.")
                }
            }
        }
    }

    fun forceAddMovie() {
        val state = _uiState.value
        val capturedBarcode = resolveCapturedBarcode(state)
        viewModelScope.launch {
            val result = movieRepository.addForceMovie(
                title = state.title.trim(),
                year = state.year.trim(),
                upc = capturedBarcode,
            )
            if (result.isSuccess) {
                finishAfterAdd(state.title)
            } else {
                _uiState.update {
                    it.copy(actionMessage = result.exceptionOrNull()?.message ?: "Could not force add movie.")
                }
            }
        }
    }

    fun requestDiscard() {
        _uiState.update { it.copy(showDiscardDialog = true) }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard() {
        scanSessionHolder.finishScan()
        _uiState.update { it.copy(finished = true, addedTitle = null) }
    }

    fun skipMovie() {
        scanSessionHolder.finishScan()
        _uiState.update { it.copy(finished = true, addedTitle = null) }
    }

    private fun finishAfterAdd(title: String) {
        scanSessionHolder.finishScan(addedTitle = title)
        _uiState.update {
            it.copy(
                finished = true,
                addedTitle = title,
            )
        }
    }

    private fun resolveCapturedBarcode(state: ReviewUiState): String? =
        state.barcode.trim().takeIf { barcode -> barcode.isNotBlank() }

    private fun removeNewlinesFromBarcode(value: String): String =
        value.filter { character -> character != '\n' && character != '\r' }

    private fun buildBarcodeLlmMessage(
        upc: String?,
        barcodeGuess: MovieGuess?,
    ): String {
        if (upc.isNullOrBlank()) {
            return "No barcode was captured; the LLM did not look up a barcode."
        }
        if (barcodeGuess == null) {
            return "The LLM did not identify this barcode."
        }
        val barcodeTitle = barcodeGuess.title.trim()
        val barcodeYear = barcodeGuess.year.trim()
        if (barcodeTitle.isBlank() && barcodeYear.isBlank()) {
            return "The LLM did not identify this barcode."
        }
        return when {
            barcodeTitle.isNotBlank() && barcodeYear.isNotBlank() ->
                "The LLM identified this barcode as $barcodeTitle ($barcodeYear)."
            barcodeTitle.isNotBlank() ->
                "The LLM identified this barcode as $barcodeTitle."
            else ->
                "The LLM identified this barcode with year $barcodeYear."
        }
    }

    private fun buildBarcodeUsageMessage(
        upc: String?,
        coverGuess: MovieGuess?,
        barcodeGuess: MovieGuess?,
    ): String? {
        if (upc.isNullOrBlank()) {
            return null
        }
        val coverTitle = coverGuess?.title?.trim().orEmpty()
        val coverYear = coverGuess?.year?.trim().orEmpty()
        val barcodeTitle = barcodeGuess?.title?.trim().orEmpty()
        val barcodeYear = barcodeGuess?.year?.trim().orEmpty()
        val usedForTitle = coverTitle.isBlank() && barcodeTitle.isNotBlank()
        val usedForYear = coverYear.isBlank() && barcodeYear.isNotBlank()
        return when {
            usedForTitle && usedForYear -> "Barcode was used to find the title and year."
            usedForTitle -> "Barcode was used to find the title."
            usedForYear -> "Barcode was used to find the year."
            else -> "Barcode was not used to find the title or year."
        }
    }

    private suspend fun refreshActionState() {
        val state = _uiState.value
        val titleFilled = state.title.trim().isNotEmpty()
        val yearFilled = state.year.trim().isNotEmpty()
        val selected = state.selectedTmdbResult
        val isDataIncomplete = selected == null
        val willOverwriteTmdbMatch = selected?.let { movieRepository.existsByTmdbId(it.id) } ?: false
        val willOverwriteTitleAndYear = if (titleFilled && yearFilled) {
            movieRepository.existsByTitleAndYear(state.title.trim(), state.year.trim())
        } else {
            false
        }
        _uiState.update {
            it.copy(
                isAddEnabled = yearFilled && selected != null,
                showReplaceAdd = willOverwriteTmdbMatch,
                showForceAdd = titleFilled && yearFilled && isDataIncomplete,
                isForceAddEnabled = titleFilled && yearFilled && isDataIncomplete,
                duplicateMessage = when {
                    willOverwriteTmdbMatch -> "Already in list. Add will replace the existing entry."
                    willOverwriteTitleAndYear && isDataIncomplete ->
                        "Already in list. Force Add will replace the existing entry."
                    else -> null
                },
            )
        }
    }
}
