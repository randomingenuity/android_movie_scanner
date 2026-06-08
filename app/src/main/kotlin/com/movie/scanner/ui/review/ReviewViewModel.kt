package com.movie.scanner.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.ReviewItemDetails
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.data.repository.TmdbRepository
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.IntegerInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val featureType: FeatureType = FeatureType.MOVIE,
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
    val showForceReplace: Boolean = false,
    val isForceAddEnabled: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val finished: Boolean = false,
    val addedTitle: String? = null,
    val discType: String? = null,
    val location: String = "",
    val seasonNumberInput: String = "",
    val numberOfDiscsInput: String = "",
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val scanSessionHolder: ScanSessionHolder,
    private val tmdbRepository: TmdbRepository,
    private val movieRepository: MovieRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()
    private var loadedExistingEntryKey: String? = null

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
            featureType = scanSessionHolder.lastReviewFeatureType,
            location = scanSessionHolder.lastReviewLocation,
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

    fun updateFeatureType(featureType: FeatureType) {
        scanSessionHolder.rememberReviewFeatureType(featureType)
        _uiState.update {
            if (featureType == FeatureType.TV) {
                it.copy(featureType = featureType)
            } else {
                it.copy(
                    featureType = featureType,
                    seasonNumberInput = "",
                )
            }
        }
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

    fun updateDiscType(discType: String?) {
        _uiState.update { it.copy(discType = discType, actionMessage = null) }
    }

    fun updateLocation(value: String) {
        scanSessionHolder.rememberReviewLocation(value)
        _uiState.update { it.copy(location = value, actionMessage = null) }
    }

    fun updateSeasonNumberInput(value: String) {
        _uiState.update {
            it.copy(
                seasonNumberInput = IntegerInput.filterDigits(value),
                duplicateMessage = null,
                actionMessage = null,
            )
        }
        viewModelScope.launch { refreshActionState() }
    }

    fun updateNumberOfDiscsInput(value: String) {
        _uiState.update {
            it.copy(
                numberOfDiscsInput = IntegerInput.filterDigits(value),
                actionMessage = null,
            )
        }
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
        val details = buildReviewItemDetails(state) ?: return
        viewModelScope.launch {
            val result = movieRepository.addMatchedMovie(
                title = state.title.trim(),
                year = state.year.trim(),
                upc = capturedBarcode,
                match = selected,
                details = details,
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
        val details = buildReviewItemDetails(state) ?: return
        viewModelScope.launch {
            val result = movieRepository.addForceMovie(
                title = state.title.trim(),
                year = state.year.trim(),
                upc = capturedBarcode,
                details = details,
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
        scanSessionHolder.rememberReviewLocation(_uiState.value.location)
        scanSessionHolder.finishScan(addedTitle = title)
        _uiState.update {
            it.copy(
                finished = true,
                addedTitle = title,
            )
        }
    }

    private fun buildReviewItemDetails(state: ReviewUiState): ReviewItemDetails? {
        val seasonNumber = if (state.featureType == FeatureType.TV) {
            IntegerInput.parseOptionalInt(state.seasonNumberInput)
        } else {
            null
        }
        if (state.featureType == FeatureType.TV && state.seasonNumberInput.isBlank()) {
            _uiState.update { it.copy(actionMessage = "Season is required.") }
            return null
        }
        if (state.featureType == FeatureType.TV && seasonNumber == null) {
            _uiState.update { it.copy(actionMessage = "Season must be a whole number.") }
            return null
        }
        val numberOfDiscs = IntegerInput.parseOptionalInt(state.numberOfDiscsInput)
        if (state.numberOfDiscsInput.isNotBlank() && numberOfDiscs == null) {
            _uiState.update { it.copy(actionMessage = "Number of Discs must be a whole number.") }
            return null
        }
        return ReviewItemDetails(
            featureType = state.featureType,
            discType = state.discType,
            location = state.location.trim().takeIf { location -> location.isNotBlank() },
            seasonNumber = seasonNumber,
            numberOfDiscs = numberOfDiscs,
        )
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
        val seasonNumber = parseEnteredSeasonNumber(state)
        val seasonFilled = state.featureType != FeatureType.TV || seasonNumber != null
        val willOverwriteTitleAndSeason = if (
            state.featureType == FeatureType.TV &&
            titleFilled &&
            seasonNumber != null
        ) {
            movieRepository.existsByTitleAndSeason(state.title.trim(), seasonNumber)
        } else {
            false
        }
        val willOverwriteTmdbMatch = if (state.featureType == FeatureType.MOVIE) {
            selected?.let { movieRepository.existsByTmdbId(it.id) } ?: false
        } else {
            false
        }
        val willOverwriteTitleAndYear = if (
            state.featureType == FeatureType.MOVIE &&
            titleFilled &&
            yearFilled
        ) {
            movieRepository.existsByTitleAndYear(state.title.trim(), state.year.trim())
        } else {
            false
        }
        val willOverwriteExisting = when (state.featureType) {
            FeatureType.TV -> willOverwriteTitleAndSeason
            FeatureType.MOVIE -> willOverwriteTmdbMatch || willOverwriteTitleAndYear
        }
        if (willOverwriteExisting) {
            applyExistingEntryMetadata(state)
        } else {
            clearLoadedExistingEntryMetadata()
        }
        val showReplaceAdd = when (state.featureType) {
            FeatureType.TV -> selected != null && willOverwriteTitleAndSeason
            FeatureType.MOVIE -> willOverwriteTmdbMatch
        }
        val showForceAdd = titleFilled && yearFilled && seasonFilled && isDataIncomplete
        val showForceReplace = when (state.featureType) {
            FeatureType.TV -> showForceAdd && willOverwriteTitleAndSeason
            FeatureType.MOVIE -> showForceAdd && willOverwriteTitleAndYear
        }
        _uiState.update {
            it.copy(
                isAddEnabled = yearFilled && seasonFilled && selected != null,
                showReplaceAdd = showReplaceAdd,
                showForceAdd = showForceAdd,
                showForceReplace = showForceReplace,
                isForceAddEnabled = showForceAdd,
                duplicateMessage = when {
                    showReplaceAdd -> "Already in list. Replace will replace the existing entry."
                    showForceReplace -> "Already in list. Force Replace will replace the existing entry."
                    else -> null
                },
            )
        }
    }

    private fun parseEnteredSeasonNumber(state: ReviewUiState): Int? {
        if (state.featureType != FeatureType.TV || state.seasonNumberInput.isBlank()) {
            return null
        }
        return IntegerInput.parseOptionalInt(state.seasonNumberInput)
    }

    private suspend fun resolveExistingMovie(state: ReviewUiState): MovieEntity? {
        val title = state.title.trim()
        if (state.featureType == FeatureType.TV) {
            val seasonNumber = parseEnteredSeasonNumber(state) ?: return null
            if (movieRepository.existsByTitleAndSeason(title, seasonNumber)) {
                return movieRepository.findByTitleAndSeason(title, seasonNumber)
            }
            return null
        }
        val selected = state.selectedTmdbResult
        if (selected != null && movieRepository.existsByTmdbId(selected.id)) {
            return movieRepository.findByTmdbId(selected.id)
        }
        val year = state.year.trim()
        if (title.isNotEmpty() && year.isNotEmpty() && movieRepository.existsByTitleAndYear(title, year)) {
            return movieRepository.findByTitleAndYear(title, year)
        }
        return null
    }

    private suspend fun applyExistingEntryMetadata(state: ReviewUiState) {
        val existingMovie = resolveExistingMovie(state) ?: return
        val entryKey = "movie-${existingMovie.id}"
        if (loadedExistingEntryKey == entryKey) {
            return
        }
        loadedExistingEntryKey = entryKey
        val featureType = FeatureType.fromLabel(existingMovie.featureType)
        scanSessionHolder.rememberReviewFeatureType(featureType)
        _uiState.update {
            it.copy(
                featureType = featureType,
                discType = existingMovie.discType,
                location = existingMovie.location.orEmpty(),
                seasonNumberInput = existingMovie.seasonNumber?.toString().orEmpty(),
                numberOfDiscsInput = existingMovie.numberOfDiscs?.toString().orEmpty(),
                barcode = it.barcode.takeIf { barcode -> barcode.isNotBlank() }
                    ?: removeNewlinesFromBarcode(existingMovie.upc.orEmpty()),
            )
        }
    }

    private fun clearLoadedExistingEntryMetadata() {
        if (loadedExistingEntryKey == null) {
            return
        }
        loadedExistingEntryKey = null
        _uiState.update {
            it.copy(
                featureType = scanSessionHolder.lastReviewFeatureType,
                discType = null,
                location = scanSessionHolder.lastReviewLocation,
                numberOfDiscsInput = "",
            )
        }
    }
}
