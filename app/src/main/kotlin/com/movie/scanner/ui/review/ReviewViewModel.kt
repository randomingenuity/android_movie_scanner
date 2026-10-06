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
import com.movie.scanner.data.session.BulkQueueSessionState
import com.movie.scanner.data.session.BulkReviewPreloadService
import com.movie.scanner.data.session.PreloadedBulkReview
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

/**
 * How title, year, and barcode were populated before any user edits on the review form.
 */
enum class ReviewAutomaticParameterSource {
    BARCODE_LOOKUP,
    COVER_IMAGE,
}

data class ReviewUiState(
    val featureType: FeatureType = FeatureType.MOVIE,
    val title: String = "",
    val year: String = "",
    val barcode: String = "",
    val extractedCoverTitle: String = "",
    val recognizedTitle: String = "",
    val recognizedYear: String = "",
    val recognizedBarcode: String = "",
    val automaticParameterSource: ReviewAutomaticParameterSource? = null,
    val barcodeSuggestion: MovieGuess? = null,
    val tmdbResults: List<TmdbSearchResult> = emptyList(),
    val selectedTmdbResult: TmdbSearchResult? = null,
    val tmdbSyncedTitle: String = "",
    val tmdbSyncedYear: String = "",
    val showDiscardDialog: Boolean = false,
    val finished: Boolean = false,
    val addedTitle: String? = null,
    val discType: String? = null,
    val edition: String? = null,
    val location: String = "",
    val seasonNumberInput: String = "",
    val numberOfDiscsInput: Int = ReviewViewModel.DEFAULT_NUMBER_OF_DISCS,
    val isBulkProcessing: Boolean = false,
    val bulkCoverAbsolutePath: String? = null,
    val showBulkCoverPreview: Boolean = false,
    val finishedFromBulkProcessing: Boolean = false,
    val requiresManualTitleEntry: Boolean = false,
)

/**
 * Add/search action affordances updated after duplicate checks; kept separate so scroll does not
 * recompose the whole form when only button labels or enablement change.
 */
data class ReviewActionState(
    val isBackEnabled: Boolean = false,
    val isAddEnabled: Boolean = false,
    val showReplaceAdd: Boolean = false,
    val showForceAdd: Boolean = false,
    val showForceReplace: Boolean = false,
    val isForceAddEnabled: Boolean = false,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val duplicateMessage: String? = null,
    val addDisabledReason: String? = null,
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
    val numberOfDiscsInput: Int,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val scanSessionHolder: ScanSessionHolder,
    private val tmdbRepository: TmdbRepository,
    private val movieRepository: MovieRepository,
    private val bulkImageRepository: BulkImageRepository,
    private val bulkQueueSessionState: BulkQueueSessionState,
    private val bulkReviewPreloadService: BulkReviewPreloadService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()
    private val _actionState = MutableStateFlow(ReviewActionState())
    val actionState: StateFlow<ReviewActionState> = _actionState.asStateFlow()
    private val _bulkReviewSessionKey = MutableStateFlow(0)
    val bulkReviewSessionKey: StateFlow<Int> = _bulkReviewSessionKey.asStateFlow()
    private val navigationEvents = Channel<ReviewNavigationEvent>(Channel.BUFFERED)
    val navigationEventFlow = navigationEvents.receiveAsFlow()
    val reviewPayloadGeneration = scanSessionHolder.reviewPayloadGeneration
    private var loadedExistingEntryKey: String? = null
    private var lastConsumedReviewPayloadGeneration: Long = -1L
    private var initialTmdbSearchAttemptedForPayloadGeneration: Long = -1L
    private var refreshActionStateJob: Job? = null
    private var titleUpdateJob: Job? = null
    private var yearUpdateJob: Job? = null
    private var seasonUpdateJob: Job? = null
    private var barcodeUpdateJob: Job? = null
    private var locationUpdateJob: Job? = null

    init {
        _uiState.value = buildReviewUiStateFromSession()
        markReviewPayloadConsumed()
        ensureInitialTmdbMatch()
        if (scanSessionHolder.isBulkProcessing) {
            scanSessionHolder.currentBulkRecordId?.let { recordId ->
                bulkReviewPreloadService.schedulePreloadAfter(recordId)
            }
        }
        viewModelScope.launch { refreshActionStateNow() }
    }

    /**
     * Rebuilds review UI when a new recognition payload was stored on the scan session.
     */
    fun consumeReviewPayloadFromSessionIfNeeded() {
        val generation = scanSessionHolder.reviewPayloadGeneration.value
        if (generation == lastConsumedReviewPayloadGeneration) {
            return
        }
        lastConsumedReviewPayloadGeneration = generation
        initialTmdbSearchAttemptedForPayloadGeneration = -1L
        loadedExistingEntryKey = null
        cancelPendingFieldUpdates()
        clearActionMessages()
        _uiState.value = buildReviewUiStateFromSession()
        _bulkReviewSessionKey.update { sessionKey -> sessionKey + 1 }
        ensureInitialTmdbMatch()
        viewModelScope.launch { refreshActionStateNow() }
    }

    /**
     * Copies TMDB results from the scan session into UI state, or searches TMDB when title and year
     * are already known but no match was loaded.
     */
    fun ensureInitialTmdbMatch() {
        val generation = scanSessionHolder.reviewPayloadGeneration.value
        val state = _uiState.value
        val sessionResults = scanSessionHolder.initialTmdbResults
        if (state.tmdbResults.isEmpty() && sessionResults.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    tmdbResults = sessionResults,
                    selectedTmdbResult = sessionResults.firstOrNull(),
                )
            }
            viewModelScope.launch { refreshActionStateNow() }
            return
        }
        if (state.selectedTmdbResult != null || state.tmdbResults.isNotEmpty()) {
            return
        }
        if (initialTmdbSearchAttemptedForPayloadGeneration == generation) {
            return
        }
        val title = state.title.trim()
        val year = state.year.trim()
        if (title.isEmpty() || year.isEmpty()) {
            return
        }
        initialTmdbSearchAttemptedForPayloadGeneration = generation
        viewModelScope.launch {
            runInitialTmdbSearch(title = title, year = year)
        }
    }

    private suspend fun runInitialTmdbSearch(title: String, year: String) {
        if (_uiState.value.selectedTmdbResult != null || _uiState.value.tmdbResults.isNotEmpty()) {
            return
        }
        _actionState.update { it.copy(isSearching = true, searchError = null) }
        val result = tmdbRepository.searchMovies(title, year)
        _uiState.update { currentState ->
            if (result.isSuccess) {
                val results = result.getOrDefault(emptyList())
                currentState.copy(
                    tmdbResults = results,
                    selectedTmdbResult = results.firstOrNull(),
                    tmdbSyncedTitle = title,
                    tmdbSyncedYear = year,
                )
            } else {
                currentState
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

    /**
     * Returns the TMDB row for the Matched summary line from review UI state.
     */
    fun resolveMatchedTmdbResultForDisplay(): TmdbSearchResult? {
        val state = _uiState.value
        return state.selectedTmdbResult ?: state.tmdbResults.firstOrNull()
    }

    private fun markReviewPayloadConsumed() {
        lastConsumedReviewPayloadGeneration = scanSessionHolder.reviewPayloadGeneration.value
    }

    fun updateFeatureType(featureType: FeatureType) {
        scanSessionHolder.rememberReviewFeatureType(featureType)
        _uiState.update {
            if (featureType == FeatureType.TV) {
                it.copy(
                    featureType = featureType,
                    edition = null,
                )
            } else {
                it.copy(
                    featureType = featureType,
                    seasonNumberInput = "",
                    edition = null,
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
                numberOfDiscsInput = clampNumberOfDiscs(formFields.numberOfDiscsInput),
            )
        }
        scanSessionHolder.rememberReviewLocation(formFields.location)
        clearActionMessages()
    }

    fun updateDiscType(discType: String?) {
        _uiState.update { it.copy(discType = discType) }
        clearActionMessages()
        viewModelScope.launch { refreshActionStateNow() }
    }

    fun updateEdition(edition: String?) {
        scanSessionHolder.rememberReviewEdition(edition)
        _uiState.update { it.copy(edition = edition) }
        clearActionMessages()
        viewModelScope.launch { refreshActionStateNow() }
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

    fun updateNumberOfDiscsInput(value: Int) {
        _uiState.update {
            it.copy(numberOfDiscsInput = clampNumberOfDiscs(value))
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
        val syncedYear = result.year.ifBlank { _uiState.value.year }
        _uiState.update {
            it.copy(
                selectedTmdbResult = result,
                title = result.title,
                year = syncedYear,
                tmdbSyncedTitle = result.title,
                tmdbSyncedYear = syncedYear,
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
                        tmdbSyncedTitle = title,
                        tmdbSyncedYear = year.orEmpty(),
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
                finishAfterAdd(
                    title = state.title,
                    addedMovieId = result.getOrThrow(),
                )
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
                finishAfterAdd(
                    title = state.title,
                    addedMovieId = result.getOrThrow(),
                )
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

    /**
     * Discards the current bulk item and reloads the last movie added to the list for editing.
     */
    fun goBackToLastAddedMovie() {
        viewModelScope.launch {
            val movieId = bulkQueueSessionState.lastAddedMovieId ?: return@launch
            val movie = movieRepository.findById(movieId) ?: return@launch
            val currentRecordId = scanSessionHolder.currentBulkRecordId
            if (currentRecordId != null) {
                bulkQueueSessionState.deferRecord(currentRecordId)
            }
            bulkReviewPreloadService.clearPreload()
            val bulkRecordId = bulkQueueSessionState.lastProcessedBulkRecordId
            val bulkCoverRelFilepath = bulkRecordId?.let { recordId ->
                bulkImageRepository.getRecordById(recordId)?.coverRelFilepath
            }
            bulkQueueSessionState.clearLastAddedEntry()
            bulkQueueSessionState.processingRecordId = bulkRecordId
            if (bulkRecordId != null && bulkCoverRelFilepath != null) {
                scanSessionHolder.startNewScan()
                scanSessionHolder.startBulkItem(
                    recordId = bulkRecordId,
                    coverRelFilepath = bulkCoverRelFilepath,
                )
            }
            applyMovieEntityToUi(
                movie = movie,
                bulkCoverRelFilepath = bulkCoverRelFilepath,
            )
            bulkRecordId?.let { recordId ->
                bulkReviewPreloadService.schedulePreloadAfter(recordId)
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
        viewModelScope.launch {
            if (tryAdvanceToNextBulkItem(markCurrentProcessed = false)) {
                return@launch
            }
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
            if (tryAdvanceToNextBulkItem(markCurrentProcessed = false)) {
                return@launch
            }
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

    private fun finishAfterAdd(title: String, addedMovieId: Long) {
        viewModelScope.launch {
            if (scanSessionHolder.isBulkProcessing) {
                bulkQueueSessionState.rememberLastAddedEntry(
                    movieId = addedMovieId,
                    bulkRecordId = scanSessionHolder.currentBulkRecordId,
                )
            }
            if (tryAdvanceToNextBulkItem(markCurrentProcessed = true)) {
                return@launch
            }
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

    /**
     * Applies a preloaded bulk item on the review screen without leaving bulk processing.
     */
    private suspend fun tryAdvanceToNextBulkItem(
        markCurrentProcessed: Boolean,
    ): Boolean {
        if (!scanSessionHolder.isBulkProcessing || scanSessionHolder.bulkProcessingStopRequested) {
            return false
        }
        val currentRecordId = scanSessionHolder.currentBulkRecordId ?: return false
        if (markCurrentProcessed) {
            bulkImageRepository.markProcessed(currentRecordId)
        } else {
            bulkQueueSessionState.deferRecord(currentRecordId)
        }
        val preloadedReview = bulkReviewPreloadService.takePreloadedReview() ?: return false
        bulkQueueSessionState.processingRecordId = preloadedReview.recordId
        scanSessionHolder.startNewScan()
        scanSessionHolder.startBulkItem(
            recordId = preloadedReview.recordId,
            coverRelFilepath = preloadedReview.coverRelFilepath,
        )
        scanSessionHolder.storeRecognitionResults(
            coverGuessValue = preloadedReview.coverGuess,
            barcodeGuessValue = preloadedReview.barcodeGuess,
            tmdbResults = preloadedReview.tmdbResults,
            capturedUpcValue = preloadedReview.capturedUpc,
        )
        if (markCurrentProcessed) {
            val savedLocation = _uiState.value.location
            if (savedLocation.isNotBlank()) {
                scanSessionHolder.rememberReviewLocation(savedLocation)
            }
        }
        applyPreloadedReviewToUi(preloadedReview)
        bulkReviewPreloadService.schedulePreloadAfter(preloadedReview.recordId)
        return true
    }

    private fun applyMovieEntityToUi(
        movie: MovieEntity,
        bulkCoverRelFilepath: String?,
    ) {
        loadedExistingEntryKey = "movie-${movie.id}"
        cancelPendingFieldUpdates()
        clearActionMessages()
        _uiState.value = buildReviewUiStateFromMovie(
            movie = movie,
            bulkCoverRelFilepath = bulkCoverRelFilepath,
            isBulkProcessing = scanSessionHolder.isBulkProcessing,
        )
        markReviewPayloadConsumed()
        _bulkReviewSessionKey.update { sessionKey -> sessionKey + 1 }
        viewModelScope.launch { refreshActionStateNow() }
    }

    private fun buildReviewUiStateFromMovie(
        movie: MovieEntity,
        bulkCoverRelFilepath: String?,
        isBulkProcessing: Boolean,
    ): ReviewUiState {
        val featureType = FeatureType.fromLabel(movie.featureType)
        val tmdbResults = if (movie.tmdbId != null && movie.tmdbUrl != null) {
            listOf(
                TmdbSearchResult(
                    id = movie.tmdbId,
                    title = movie.title,
                    year = movie.year,
                    posterUrl = movie.posterUrl,
                    tmdbUrl = movie.tmdbUrl,
                ),
            )
        } else {
            emptyList()
        }
        val selectedTmdbResult = tmdbResults.firstOrNull()
        scanSessionHolder.rememberReviewFeatureType(featureType)
        if (!movie.location.isNullOrBlank()) {
            scanSessionHolder.rememberReviewLocation(movie.location)
        }
        scanSessionHolder.rememberReviewDiscType(movie.discType)
        scanSessionHolder.rememberReviewEdition(
            if (featureType == FeatureType.MOVIE) {
                movie.edition
            } else {
                null
            },
        )
        val barcode = removeNewlinesFromBarcode(movie.upc.orEmpty())
        return ReviewUiState(
            featureType = featureType,
            title = movie.title,
            year = movie.year,
            barcode = barcode,
            recognizedTitle = movie.title,
            recognizedYear = movie.year,
            recognizedBarcode = barcode,
            automaticParameterSource = null,
            tmdbResults = tmdbResults,
            selectedTmdbResult = selectedTmdbResult,
            tmdbSyncedTitle = movie.title,
            tmdbSyncedYear = movie.year,
            discType = movie.discType,
            edition = if (featureType == FeatureType.MOVIE) {
                movie.edition
            } else {
                null
            },
            location = movie.location.orEmpty(),
            seasonNumberInput = movie.seasonNumber?.toString().orEmpty(),
            numberOfDiscsInput = clampNumberOfDiscs(
                movie.numberOfDiscs ?: DEFAULT_NUMBER_OF_DISCS,
            ),
            isBulkProcessing = isBulkProcessing,
            bulkCoverAbsolutePath = bulkCoverRelFilepath?.let { relativePath ->
                bulkImageRepository.resolveAbsolutePath(relativePath)
            },
        )
    }

    private fun applyPreloadedReviewToUi(preloadedReview: PreloadedBulkReview) {
        loadedExistingEntryKey = null
        cancelPendingFieldUpdates()
        clearActionMessages()
        _uiState.value = buildReviewUiState(
            coverGuess = preloadedReview.coverGuess,
            barcodeGuess = preloadedReview.barcodeGuess,
            initialResults = preloadedReview.tmdbResults,
            capturedBarcode = preloadedReview.capturedUpc,
            bulkCoverRelFilepath = preloadedReview.coverRelFilepath,
            isBulkProcessing = true,
        )
        markReviewPayloadConsumed()
        _bulkReviewSessionKey.update { sessionKey -> sessionKey + 1 }
        viewModelScope.launch { refreshActionStateNow() }
    }

    private fun buildReviewUiStateFromSession(): ReviewUiState =
        buildReviewUiState(
            coverGuess = scanSessionHolder.coverGuess,
            barcodeGuess = scanSessionHolder.barcodeGuess,
            initialResults = scanSessionHolder.initialTmdbResults,
            capturedBarcode = scanSessionHolder.resolveCapturedUpc(),
            bulkCoverRelFilepath = scanSessionHolder.bulkCoverRelFilepath,
            isBulkProcessing = scanSessionHolder.isBulkProcessing,
        )

    private fun buildReviewUiState(
        coverGuess: MovieGuess?,
        barcodeGuess: MovieGuess?,
        initialResults: List<TmdbSearchResult>,
        capturedBarcode: String?,
        bulkCoverRelFilepath: String?,
        isBulkProcessing: Boolean,
    ): ReviewUiState {
        val title = coverGuess?.title?.takeIf { title -> title.isNotBlank() }
            ?: barcodeGuess?.title.orEmpty()
        val year = coverGuess?.year?.takeIf { year -> year.isNotBlank() }
            ?: barcodeGuess?.year.orEmpty()
        val barcodeSuggestion = barcodeGuess?.takeIf {
            it.title.isNotBlank() &&
                (it.title != title || it.year != year)
        }
        val barcode = removeNewlinesFromBarcode(capturedBarcode.orEmpty())
        return ReviewUiState(
            featureType = scanSessionHolder.lastReviewFeatureType,
            discType = resolveDefaultReviewDiscType(),
            edition = null,
            location = resolveDefaultReviewLocation(),
            title = title,
            year = year,
            barcode = barcode,
            extractedCoverTitle = coverGuess?.title?.trim().orEmpty(),
            recognizedTitle = title,
            recognizedYear = year,
            recognizedBarcode = barcode,
            automaticParameterSource = resolveAutomaticParameterSource(
                coverGuess = coverGuess,
                barcodeGuess = barcodeGuess,
                capturedBarcode = capturedBarcode,
            ),
            barcodeSuggestion = barcodeSuggestion,
            tmdbResults = initialResults,
            selectedTmdbResult = initialResults.firstOrNull(),
            tmdbSyncedTitle = title,
            tmdbSyncedYear = year,
            isBulkProcessing = isBulkProcessing,
            bulkCoverAbsolutePath = bulkCoverRelFilepath?.let { relativePath ->
                bulkImageRepository.resolveAbsolutePath(relativePath)
            },
            requiresManualTitleEntry = resolveRequiresManualTitleEntry(
                coverGuess = coverGuess,
                capturedBarcode = capturedBarcode,
            ),
        )
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
        return ReviewItemDetails(
            featureType = state.featureType,
            discType = state.discType,
            edition = if (state.featureType == FeatureType.MOVIE) {
                state.edition
            } else {
                null
            },
            location = state.location.trim().takeIf { location -> location.isNotBlank() },
            seasonNumber = seasonNumber,
            numberOfDiscs = clampNumberOfDiscs(state.numberOfDiscsInput),
        )
    }

    private fun resolveCapturedBarcode(state: ReviewUiState): String? =
        state.barcode.trim().takeIf { barcode -> barcode.isNotBlank() }

    private fun removeNewlinesFromBarcode(value: String): String =
        normalizeReviewBarcode(value)

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
        val currentState = _uiState.value
        val seasonNumberAfterMetadata = parseEnteredSeasonNumber(currentState)
        val willOverwriteTitleAndSeasonNow = if (
            currentState.featureType == FeatureType.TV &&
            currentState.title.trim().isNotEmpty() &&
            seasonNumberAfterMetadata != null
        ) {
            movieRepository.existsByTitleAndSeason(
                currentState.title.trim(),
                seasonNumberAfterMetadata,
            )
        } else {
            false
        }
        val selectedAfterMetadata = currentState.selectedTmdbResult
        val willOverwriteTmdbMatchNow = if (currentState.featureType == FeatureType.MOVIE) {
            selectedAfterMetadata?.let { movieRepository.existsByTmdbId(it.id) } ?: false
        } else {
            false
        }
        val discTypeFilled = !currentState.discType.isNullOrBlank()
        val editionFilled =
            currentState.featureType != FeatureType.MOVIE || !currentState.edition.isNullOrBlank()
        val seasonFilledForAdd =
            currentState.featureType != FeatureType.TV || seasonNumberAfterMetadata != null
        val showReplaceAdd = when (currentState.featureType) {
            FeatureType.TV -> selectedAfterMetadata != null && willOverwriteTitleAndSeasonNow
            FeatureType.MOVIE -> willOverwriteTmdbMatchNow
        }
        val showForceAdd = titleFilled && yearFilled && seasonFilled && isDataIncomplete
        val showForceReplace = when (state.featureType) {
            FeatureType.TV -> showForceAdd && willOverwriteTitleAndSeason
            FeatureType.MOVIE -> showForceAdd && willOverwriteTitleAndYear
        }
        val existingMovieForComparison = resolveExistingMovie(currentState)
        val formMatchesExistingListEntry =
            existingMovieForComparison != null &&
                reviewFormMatchesExistingMovie(
                    state = currentState,
                    existingMovie = existingMovieForComparison,
                    selectedTmdbResult = selectedAfterMetadata,
                    capturedBarcode = resolveCapturedBarcode(currentState),
                )
        val formReadyForAdd =
            yearFilled &&
                seasonFilledForAdd &&
                discTypeFilled &&
                editionFilled &&
                selectedAfterMetadata != null
        val isAddEnabled = formReadyForAdd && !formMatchesExistingListEntry
        val addDisabledReason = if (isAddEnabled) {
            null
        } else if (formReadyForAdd && formMatchesExistingListEntry) {
            "This entry already matches the list. Change a field or tap Skip to continue."
        } else {
            buildAddDisabledReason(
                state = currentState,
                yearFilled = yearFilled,
                seasonFilled = seasonFilledForAdd,
                discTypeFilled = discTypeFilled,
                editionFilled = editionFilled,
                selected = selectedAfterMetadata,
                willOverwriteTitleAndSeason = willOverwriteTitleAndSeasonNow,
                willOverwriteTmdbMatch = willOverwriteTmdbMatchNow,
            )
        }
        val isForceAddEnabled = showForceAdd && !formMatchesExistingListEntry
        _actionState.update {
            it.copy(
                isBackEnabled = scanSessionHolder.isBulkProcessing &&
                    bulkQueueSessionState.lastAddedMovieId != null,
                isAddEnabled = isAddEnabled,
                showReplaceAdd = showReplaceAdd,
                showForceAdd = showForceAdd,
                showForceReplace = showForceReplace,
                isForceAddEnabled = isForceAddEnabled,
                duplicateMessage = when {
                    showReplaceAdd && !isAddEnabled && !formMatchesExistingListEntry ->
                        "Already in list. Replace will replace the existing entry."
                    showForceReplace && !isForceAddEnabled && !formMatchesExistingListEntry ->
                        "Already in list. Force Replace will replace the existing entry."
                    else -> null
                },
                addDisabledReason = addDisabledReason,
            )
        }
        applyBulkBatchLocationPrefill()
        applyBulkBatchDiscTypePrefill()
    }

    private fun resolveDefaultReviewLocation(): String {
        if (scanSessionHolder.isBulkProcessing && scanSessionHolder.bulkBatchLocation.isNotBlank()) {
            return scanSessionHolder.bulkBatchLocation
        }

        return scanSessionHolder.lastReviewLocation
    }

    private fun resolveDefaultReviewDiscType(): String? {
        if (!scanSessionHolder.isBulkProcessing) {
            return null
        }
        return scanSessionHolder.bulkBatchDiscType?.takeIf { discType -> discType.isNotBlank() }
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

    /**
     * Re-applies the bulk batch disc type when duplicate checks leave the field blank.
     */
    private fun applyBulkBatchDiscTypePrefill() {
        if (!scanSessionHolder.isBulkProcessing) {
            return
        }
        val batchDiscType = resolveDefaultReviewDiscType()
        if (batchDiscType == null) {
            return
        }
        _uiState.update { state ->
            if (state.discType.isNullOrBlank()) {
                state.copy(discType = batchDiscType)
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

    private fun buildAddDisabledReason(
        state: ReviewUiState,
        yearFilled: Boolean,
        seasonFilled: Boolean,
        discTypeFilled: Boolean,
        editionFilled: Boolean,
        selected: TmdbSearchResult?,
        willOverwriteTitleAndSeason: Boolean,
        willOverwriteTmdbMatch: Boolean,
    ): String {
        if (selected == null) {
            if (state.featureType == FeatureType.TV && willOverwriteTitleAndSeason) {
                return "This title and season are already in the list. Select a TMDB match to replace it."
            }
            if (state.featureType == FeatureType.MOVIE && willOverwriteTmdbMatch) {
                return "This movie is already in the list. Select a TMDB match to replace it."
            }
            if (state.tmdbResults.isEmpty()) {
                return "Tap Refresh to search TMDB, then select a match."
            }
            return "Select a TMDB match below."
        }
        if (!yearFilled) {
            return "Enter a year."
        }
        if (state.featureType == FeatureType.TV && !seasonFilled) {
            return "Enter a season number."
        }
        if (!discTypeFilled) {
            if (state.featureType == FeatureType.TV && willOverwriteTitleAndSeason) {
                return "This title and season are already in the list. Select main feature disc type to use Replace."
            }
            if (state.featureType == FeatureType.MOVIE && willOverwriteTmdbMatch) {
                return "This movie is already in the list. Select main feature disc type to use Replace."
            }
            return "Select main feature disc type."
        }
        if (!editionFilled) {
            if (willOverwriteTmdbMatch) {
                return "This movie is already in the list. Select an edition to use Replace."
            }
            return "Select an edition."
        }
        return "Complete the required fields above."
    }

    /**
     * True when review fields match the list row that [resolveExistingMovie] would overwrite.
     */
    private fun reviewFormMatchesExistingMovie(
        state: ReviewUiState,
        existingMovie: MovieEntity,
        selectedTmdbResult: TmdbSearchResult?,
        capturedBarcode: String?,
    ): Boolean {
        if (state.title.trim() != existingMovie.title) {
            return false
        }
        if (state.year.trim() != existingMovie.year) {
            return false
        }
        if (state.featureType.label != existingMovie.featureType) {
            return false
        }
        val normalizedBarcode = capturedBarcode?.let { barcode -> normalizeReviewBarcode(barcode) }.orEmpty()
        val normalizedStoredUpc =
            existingMovie.upc?.let { upc -> normalizeReviewBarcode(upc) }.orEmpty()
        if (normalizedBarcode != normalizedStoredUpc) {
            return false
        }
        val selectedTmdbId = selectedTmdbResult?.id
        if (selectedTmdbId != existingMovie.tmdbId) {
            return false
        }
        if (state.discType != existingMovie.discType) {
            return false
        }
        val formEdition = if (state.featureType == FeatureType.MOVIE) {
            state.edition
        } else {
            null
        }
        if (formEdition != existingMovie.edition) {
            return false
        }
        val formLocation = state.location.trim().takeIf { location -> location.isNotBlank() }
        val storedLocation =
            existingMovie.location?.trim()?.takeIf { location -> location.isNotBlank() }
        if (formLocation != storedLocation) {
            return false
        }
        val formSeasonNumber = parseEnteredSeasonNumber(state)
        if (formSeasonNumber != existingMovie.seasonNumber) {
            return false
        }
        val formNumberOfDiscs = clampNumberOfDiscs(state.numberOfDiscsInput)
        val storedNumberOfDiscs = clampNumberOfDiscs(
            existingMovie.numberOfDiscs ?: DEFAULT_NUMBER_OF_DISCS,
        )
        if (formNumberOfDiscs != storedNumberOfDiscs) {
            return false
        }
        return true
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
        val batchDiscTypeFallback = resolveDefaultReviewDiscType()
        scanSessionHolder.rememberReviewFeatureType(featureType)
        _uiState.update {
            it.copy(
                featureType = featureType,
                discType = existingMovie.discType?.takeIf { discType -> discType.isNotBlank() }
                    ?: batchDiscTypeFallback,
                edition = if (featureType == FeatureType.MOVIE) {
                    existingMovie.edition
                } else {
                    null
                },
                location = storedLocation.ifBlank { batchLocationFallback },
                seasonNumberInput = existingMovie.seasonNumber?.toString().orEmpty(),
                numberOfDiscsInput = clampNumberOfDiscs(
                    existingMovie.numberOfDiscs ?: DEFAULT_NUMBER_OF_DISCS,
                ),
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
                discType = resolveDefaultReviewDiscType(),
                edition = null,
                location = resolveDefaultReviewLocation(),
                numberOfDiscsInput = DEFAULT_NUMBER_OF_DISCS,
            )
        }
    }

    private fun clampNumberOfDiscs(value: Int): Int =
        value.coerceIn(MIN_NUMBER_OF_DISCS, MAX_NUMBER_OF_DISCS)

    companion object {
        const val MIN_NUMBER_OF_DISCS = 1
        const val MAX_NUMBER_OF_DISCS = 10
        const val DEFAULT_NUMBER_OF_DISCS = MIN_NUMBER_OF_DISCS
        private const val FORM_FIELD_DEBOUNCE_MS = 300L

        /**
         * Label for the Parameters from line based on recognition source and current field values.
         */
        fun buildParametersFromComment(
            title: String,
            year: String,
            barcode: String,
            recognizedTitle: String,
            recognizedYear: String,
            recognizedBarcode: String,
            automaticParameterSource: ReviewAutomaticParameterSource?,
        ): String {
            val manual = title.trim() != recognizedTitle.trim() ||
                year.trim() != recognizedYear.trim() ||
                normalizeReviewBarcode(barcode) != recognizedBarcode.trim()
            if (manual) {
                return "manual"
            }
            return when (automaticParameterSource) {
                ReviewAutomaticParameterSource.BARCODE_LOOKUP -> "barcode lookup"
                ReviewAutomaticParameterSource.COVER_IMAGE -> "cover image"
                null -> "manual"
            }
        }

        /**
         * True when cover OCR did not supply a title and barcode lookup prefilled the form.
         */
        fun resolveAutomaticParameterSource(
            coverGuess: MovieGuess?,
            barcodeGuess: MovieGuess?,
            capturedBarcode: String?,
        ): ReviewAutomaticParameterSource {
            val coverTitle = coverGuess?.title?.trim().orEmpty()
            val barcodeTitle = barcodeGuess?.title?.trim().orEmpty()
            val barcode = capturedBarcode?.trim().orEmpty()
            if (barcode.isNotBlank() && coverTitle.isBlank() && barcodeTitle.isNotBlank()) {
                return ReviewAutomaticParameterSource.BARCODE_LOOKUP
            }
            return ReviewAutomaticParameterSource.COVER_IMAGE
        }

        fun normalizeReviewBarcode(value: String): String =
            com.movie.scanner.util.normalizeReviewBarcode(value)

        const val MANUAL_TITLE_ENTRY_WARNING =
            "We could not read a barcode or movie title from the photos. Enter the movie name and year, then tap Refresh."

        /**
         * True when neither the barcode image nor the cover image supplied a usable title.
         */
        fun resolveRequiresManualTitleEntry(
            coverGuess: MovieGuess?,
            capturedBarcode: String?,
        ): Boolean {
            val coverTitle = coverGuess?.title?.trim().orEmpty()
            val barcode = capturedBarcode?.trim().orEmpty()
            return barcode.isBlank() && coverTitle.isBlank()
        }

        /**
         * Returns the TMDB row to show on the Matched line (selected match, else first result).
         */
        fun resolveMatchedTmdbResult(uiState: ReviewUiState): TmdbSearchResult? =
            uiState.selectedTmdbResult ?: uiState.tmdbResults.firstOrNull()
    }
}
