package com.movie.scanner.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.ReviewItemDetails
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.data.repository.TmdbRepository
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.IntegerInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReviewNavigationEvent {
    data object NavigateToBulkRescan : ReviewNavigationEvent
}

data class ReviewUiState(
    val featureType: FeatureType = FeatureType.MOVIE,
    val title: String = "",
    val year: String = "",
    val barcode: String = "",
    val extractedCoverTitle: String = "",
    val barcodeUsedForTitle: Boolean = false,
    val barcodeUsageMessage: String? = null,
    val barcodeLlmMessage: String = "",
    val barcodeSuggestion: MovieGuess? = null,
    val tmdbResults: List<TmdbSearchResult> = emptyList(),
    val selectedTmdbResult: TmdbSearchResult? = null,
    val showDiscardDialog: Boolean = false,
    val finished: Boolean = false,
    val addedTitle: String? = null,
    val discType: String? = null,
    val location: String = "",
    val seasonNumberInput: String = "",
    val numberOfDiscsInput: String = "",
    val isBulkProcessing: Boolean = false,
    val bulkCoverAbsolutePath: String? = null,
    val showBulkCoverPreview: Boolean = false,
    val finishedFromBulkProcessing: Boolean = false,
)

/**
 * Add/search action affordances updated after duplicate checks; kept separate so scroll does not
 * recompose the whole form when only button labels or enablement change.
 */
data class ReviewActionState(
    val isAddEnabled: Boolean = false,
    val showReplaceAdd: Boolean = false,
    val showForceAdd: Boolean = false,
    val showForceReplace: Boolean = false,
    val isForceAddEnabled: Boolean = false,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val duplicateMessage: String? = null,
    val actionMessage: String? = null,
)

/**
 * Editable review form values collected from the UI before submit or TMDB search.
 */
data class ReviewFormFields(
    val title: String,
    val year: String,
    val barcode: String,
    val location: String,
    val seasonNumberInput: String,
    val numberOfDiscsInput: String,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val scanSessionHolder: ScanSessionHolder,
    private val tmdbRepository: TmdbRepository,
    private val movieRepository: MovieRepository,
    private val bulkImageRepository: BulkImageRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()
    private val _actionState = MutableStateFlow(ReviewActionState())
    val actionState: StateFlow<ReviewActionState> = _actionState.asStateFlow()
    private val navigationEvents = Channel<ReviewNavigationEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    private var loadedExistingEntryKey: String? = null
    private var refreshActionStateJob: Job? = null
    private var titleUpdateJob: Job? = null
    private var yearUpdateJob: Job? = null
    private var seasonUpdateJob: Job? = null
    private var barcodeUpdateJob: Job? = null
    private var locationUpdateJob: Job? = null

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
        val bulkCoverRelFilepath = scanSessionHolder.bulkCoverRelFilepath
        val barcodeUsedForTitle = wasBarcodeUsedForTitle(
            coverGuess = coverGuess,
            barcodeGuess = barcodeGuess,
        )
        _uiState.value = ReviewUiState(
            featureType = scanSessionHolder.lastReviewFeatureType,
            location = resolveDefaultReviewLocation(),
            title = title,
            year = year,
            barcode = removeNewlinesFromBarcode(capturedBarcode.orEmpty()),
            extractedCoverTitle = coverGuess?.title?.trim().orEmpty(),
            barcodeUsedForTitle = barcodeUsedForTitle,
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
            isBulkProcessing = scanSessionHolder.isBulkProcessing,
            bulkCoverAbsolutePath = bulkCoverRelFilepath?.let { relativePath ->
                bulkImageRepository.resolveAbsolutePath(relativePath)
            },
        )
        viewModelScope.launch { refreshActionStateNow() }
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
        viewModelScope.launch { refreshActionStateNow() }
    }

    fun scheduleTitleUpdate(value: String) {
        titleUpdateJob?.cancel()
        titleUpdateJob = viewModelScope.launch {
            delay(FORM_FIELD_DEBOUNCE_MS)
            _uiState.update { it.copy(title = value) }
            clearActionMessages()
            refreshActionStateNow()
        }
    }

    fun scheduleYearUpdate(value: String) {
        yearUpdateJob?.cancel()
        yearUpdateJob = viewModelScope.launch {
            delay(FORM_FIELD_DEBOUNCE_MS)
            _uiState.update { it.copy(year = value) }
            clearActionMessages()
            refreshActionStateNow()
        }
    }

    fun scheduleSeasonNumberUpdate(value: String) {
        val filtered = IntegerInput.filterDigits(value)
        seasonUpdateJob?.cancel()
        seasonUpdateJob = viewModelScope.launch {
            delay(FORM_FIELD_DEBOUNCE_MS)
            _uiState.update { it.copy(seasonNumberInput = filtered) }
            clearActionMessages()
            refreshActionStateNow()
        }
    }

    fun updateBarcode(value: String): String = removeNewlinesFromBarcode(value)

    fun scheduleBarcodeUpdate(value: String) {
        val sanitized = removeNewlinesFromBarcode(value)
        barcodeUpdateJob?.cancel()
        barcodeUpdateJob = viewModelScope.launch {
            delay(FORM_FIELD_DEBOUNCE_MS)
            _uiState.update { it.copy(barcode = sanitized) }
            clearActionMessages()
        }
    }

    fun commitBarcode(value: String) {
        barcodeUpdateJob?.cancel()
        _uiState.update { it.copy(barcode = removeNewlinesFromBarcode(value)) }
        clearActionMessages()
    }

    /**
     * Applies the current form snapshot and cancels any in-flight debounced field updates.
     */
    fun applyFormFields(formFields: ReviewFormFields) {
        cancelPendingFieldUpdates()
        _uiState.update {
            it.copy(
                title = formFields.title,
                year = formFields.year,
                barcode = removeNewlinesFromBarcode(formFields.barcode),
                location = formFields.location,
                seasonNumberInput = IntegerInput.filterDigits(formFields.seasonNumberInput),
                numberOfDiscsInput = IntegerInput.filterDigits(formFields.numberOfDiscsInput),
            )
        }
        scanSessionHolder.rememberReviewLocation(formFields.location)
        clearActionMessages()
    }

    fun updateDiscType(discType: String?) {
        _uiState.update { it.copy(discType = discType) }
        clearActionMessages()
    }

    fun scheduleLocationUpdate(value: String) {
        locationUpdateJob?.cancel()
        locationUpdateJob = viewModelScope.launch {
            delay(FORM_FIELD_DEBOUNCE_MS)
            scanSessionHolder.rememberReviewLocation(value)
            _uiState.update { it.copy(location = value) }
            clearActionMessages()
        }
    }

    fun updateLocation(value: String) {
        locationUpdateJob?.cancel()
        scanSessionHolder.rememberReviewLocation(value)
        _uiState.update { it.copy(location = value) }
        clearActionMessages()
    }

    fun updateSeasonNumberInput(value: String) {
        _uiState.update {
            it.copy(seasonNumberInput = IntegerInput.filterDigits(value))
        }
        clearActionMessages()
        scheduleRefreshActionState()
    }

    fun updateNumberOfDiscsInput(value: String) {
        _uiState.update {
            it.copy(numberOfDiscsInput = IntegerInput.filterDigits(value))
        }
        clearActionMessages()
    }

    fun applyBarcodeSuggestion() {
        val suggestion = _uiState.value.barcodeSuggestion ?: return
        _uiState.update {
            it.copy(
                title = suggestion.title,
                year = suggestion.year,
            )
        }
        clearActionMessages()
        viewModelScope.launch { refreshActionStateNow() }
    }

    fun selectTmdbResult(result: TmdbSearchResult) {
        _uiState.update {
            it.copy(
                selectedTmdbResult = result,
                title = result.title,
                year = result.year.ifBlank { it.year },
            )
        }
        clearActionMessages()
        viewModelScope.launch { refreshActionStateNow() }
    }

    fun searchTmdb(formFields: ReviewFormFields) {
        applyFormFields(formFields)
        val title = formFields.title.trim()
        if (title.isEmpty()) {
            return
        }
        val year = formFields.year.trim().ifBlank { null }
        viewModelScope.launch {
            refreshActionStateNow()
            _actionState.update { it.copy(isSearching = true, searchError = null) }
            val result = tmdbRepository.searchMovies(title, year)
            _uiState.update {
                if (result.isSuccess) {
                    val results = result.getOrDefault(emptyList())
                    it.copy(
                        tmdbResults = results,
                        selectedTmdbResult = results.firstOrNull(),
                    )
                } else {
                    it
                }
            }
            _actionState.update {
                if (result.isSuccess) {
                    val results = result.getOrDefault(emptyList())
                    it.copy(
                        isSearching = false,
                        searchError = if (results.isEmpty()) "No matches found." else null,
                    )
                } else {
                    it.copy(
                        isSearching = false,
                        searchError = result.exceptionOrNull()?.message ?: "TMDB search failed.",
                    )
                }
            }
            refreshActionStateNow()
        }
    }

    fun addMovie(formFields: ReviewFormFields) {
        applyFormFields(formFields)
        viewModelScope.launch {
            refreshActionStateNow()
            val state = _uiState.value
            val selected = state.selectedTmdbResult ?: return@launch
            val capturedBarcode = resolveCapturedBarcode(state)
            val details = buildReviewItemDetails(state) ?: return@launch
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
                _actionState.update {
                    it.copy(actionMessage = result.exceptionOrNull()?.message ?: "Could not add movie.")
                }
            }
        }
    }

    fun forceAddMovie(formFields: ReviewFormFields) {
        applyFormFields(formFields)
        viewModelScope.launch {
            refreshActionStateNow()
            val state = _uiState.value
            val capturedBarcode = resolveCapturedBarcode(state)
            val details = buildReviewItemDetails(state) ?: return@launch
            val result = movieRepository.addForceMovie(
                title = state.title.trim(),
                year = state.year.trim(),
                upc = capturedBarcode,
                details = details,
            )
            if (result.isSuccess) {
                finishAfterAdd(state.title)
            } else {
                _actionState.update {
                    it.copy(actionMessage = result.exceptionOrNull()?.message ?: "Could not force add movie.")
                }
            }
        }
    }

    /**
     * Stops the bulk queue, closes review without saving, and leaves the current item unprocessed.
     */
    fun stopBulkProcessing() {
        viewModelScope.launch {
            scanSessionHolder.requestStopBulkProcessing()
            scanSessionHolder.finishBulkItem()
            scanSessionHolder.finishScan()
            _uiState.update {
                it.copy(
                    finished = true,
                    addedTitle = null,
                    isBulkProcessing = false,
                    finishedFromBulkProcessing = true,
                )
            }
        }
    }

    /**
     * Opens bulk capture to replace the current queue item's barcode and cover photos.
     */
    fun requestBulkRescan() {
        val recordId = scanSessionHolder.currentBulkRecordId ?: return
        viewModelScope.launch {
            scanSessionHolder.beginBulkRescan(recordId)
            navigationEvents.send(ReviewNavigationEvent.NavigateToBulkRescan)
        }
    }

    fun showBulkCoverPreview() {
        _uiState.update { it.copy(showBulkCoverPreview = true) }
    }

    fun dismissBulkCoverPreview() {
        _uiState.update { it.copy(showBulkCoverPreview = false) }
    }

    fun requestDiscard() {
        _uiState.update { it.copy(showDiscardDialog = true) }
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard() {
        viewModelScope.launch {
            val finishedFromBulkProcessing = scanSessionHolder.isBulkProcessing
            advanceBulkQueueWithoutProcessing()
            scanSessionHolder.finishScan()
            _uiState.update {
                it.copy(
                    finished = true,
                    addedTitle = null,
                    isBulkProcessing = false,
                    finishedFromBulkProcessing = finishedFromBulkProcessing,
                )
            }
        }
    }

    fun skipMovie() {
        viewModelScope.launch {
            val finishedFromBulkProcessing = scanSessionHolder.isBulkProcessing
            advanceBulkQueueWithoutProcessing()
            scanSessionHolder.finishScan()
            _uiState.update {
                it.copy(
                    finished = true,
                    addedTitle = null,
                    isBulkProcessing = false,
                    finishedFromBulkProcessing = finishedFromBulkProcessing,
                )
            }
        }
    }

    private fun finishAfterAdd(title: String) {
        viewModelScope.launch {
            val finishedFromBulkProcessing = scanSessionHolder.isBulkProcessing
            completeBulkItemIfNeeded()
            if (finishedFromBulkProcessing) {
                val savedLocation = _uiState.value.location
                if (savedLocation.isNotBlank()) {
                    scanSessionHolder.rememberReviewLocation(savedLocation)
                }
            } else {
                scanSessionHolder.rememberReviewLocation(_uiState.value.location)
            }
            scanSessionHolder.finishScan(addedTitle = title)
            _uiState.update {
                it.copy(
                    finished = true,
                    addedTitle = title,
                    isBulkProcessing = false,
                    finishedFromBulkProcessing = finishedFromBulkProcessing,
                )
            }
        }
    }

    /**
     * Leaves the current bulk queue item unprocessed and resumes the queue when appropriate.
     */
    private suspend fun advanceBulkQueueWithoutProcessing() {
        if (!scanSessionHolder.isBulkProcessing) {
            return
        }
        val shouldResumeQueue = !scanSessionHolder.bulkProcessingStopRequested
        scanSessionHolder.finishBulkItem()
        if (shouldResumeQueue) {
            scanSessionHolder.signalBulkQueueResume()
        }
    }

    private suspend fun completeBulkItemIfNeeded() {
        if (!scanSessionHolder.isBulkProcessing) {
            return
        }
        val recordId = scanSessionHolder.currentBulkRecordId ?: return
        val shouldResumeQueue = !scanSessionHolder.bulkProcessingStopRequested
        bulkImageRepository.markProcessed(recordId)
        scanSessionHolder.finishBulkItem()
        if (shouldResumeQueue) {
            scanSessionHolder.signalBulkQueueResume()
        }
    }

    private fun buildReviewItemDetails(state: ReviewUiState): ReviewItemDetails? {
        val seasonNumber = if (state.featureType == FeatureType.TV) {
            IntegerInput.parseOptionalInt(state.seasonNumberInput)
        } else {
            null
        }
        if (state.featureType == FeatureType.TV && state.seasonNumberInput.isBlank()) {
            _actionState.update { it.copy(actionMessage = "Season is required.") }
            return null
        }
        if (state.featureType == FeatureType.TV && seasonNumber == null) {
            _actionState.update { it.copy(actionMessage = "Season must be a whole number.") }
            return null
        }
        val numberOfDiscs = IntegerInput.parseOptionalInt(state.numberOfDiscsInput)
        if (state.numberOfDiscsInput.isNotBlank() && numberOfDiscs == null) {
            _actionState.update { it.copy(actionMessage = "Number of Discs must be a whole number.") }
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

    /**
     * True when the review title came from barcode lookup because cover OCR did not produce a title.
     */
    private fun wasBarcodeUsedForTitle(
        coverGuess: MovieGuess?,
        barcodeGuess: MovieGuess?,
    ): Boolean {
        val coverTitle = coverGuess?.title?.trim().orEmpty()
        val barcodeTitle = barcodeGuess?.title?.trim().orEmpty()
        return coverTitle.isBlank() && barcodeTitle.isNotBlank()
    }

    private fun buildBarcodeUsageMessage(
        upc: String?,
        coverGuess: MovieGuess?,
        barcodeGuess: MovieGuess?,
    ): String? {
        if (upc.isNullOrBlank()) {
            return null
        }
        val coverYear = coverGuess?.year?.trim().orEmpty()
        val barcodeYear = barcodeGuess?.year?.trim().orEmpty()
        val usedForTitle = wasBarcodeUsedForTitle(coverGuess, barcodeGuess)
        val usedForYear = coverYear.isBlank() && barcodeYear.isNotBlank()
        return when {
            usedForTitle && usedForYear -> "Barcode was used to find the title and year."
            usedForTitle -> "Barcode was used to find the title."
            usedForYear -> "Barcode was used to find the year."
            else -> "Barcode was not used to find the title or year."
        }
    }

    private fun cancelPendingFieldUpdates() {
        refreshActionStateJob?.cancel()
        titleUpdateJob?.cancel()
        yearUpdateJob?.cancel()
        seasonUpdateJob?.cancel()
        barcodeUpdateJob?.cancel()
        locationUpdateJob?.cancel()
    }

    private fun clearActionMessages() {
        _actionState.update {
            it.copy(
                duplicateMessage = null,
                actionMessage = null,
            )
        }
    }

    private fun scheduleRefreshActionState() {
        refreshActionStateJob?.cancel()
        refreshActionStateJob = viewModelScope.launch {
            delay(FORM_FIELD_DEBOUNCE_MS)
            refreshActionStateNow()
        }
    }

    private suspend fun refreshActionStateNow() {
        refreshActionState()
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
        _actionState.update {
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
        applyBulkBatchLocationPrefill()
    }

    private fun resolveDefaultReviewLocation(): String {
        if (scanSessionHolder.isBulkProcessing && scanSessionHolder.bulkBatchLocation.isNotBlank()) {
            return scanSessionHolder.bulkBatchLocation
        }

        return scanSessionHolder.lastReviewLocation
    }

    /**
     * Re-applies the bulk batch location when duplicate checks leave the field blank.
     */
    private fun applyBulkBatchLocationPrefill() {
        if (!scanSessionHolder.isBulkProcessing) {
            return
        }
        val batchLocation = scanSessionHolder.bulkBatchLocation
        if (batchLocation.isBlank()) {
            return
        }
        _uiState.update { state ->
            if (state.location.isBlank()) {
                state.copy(location = batchLocation)
            } else {
                state
            }
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
        val storedLocation = existingMovie.location.orEmpty()
        val batchLocationFallback = if (scanSessionHolder.isBulkProcessing) {
            scanSessionHolder.bulkBatchLocation
        } else {
            ""
        }
        scanSessionHolder.rememberReviewFeatureType(featureType)
        _uiState.update {
            it.copy(
                featureType = featureType,
                discType = existingMovie.discType,
                location = storedLocation.ifBlank { batchLocationFallback },
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
                location = resolveDefaultReviewLocation(),
                numberOfDiscsInput = "",
            )
        }
    }

    private companion object {
        const val FORM_FIELD_DEBOUNCE_MS = 300L
    }
}
