package com.movie.scanner.ui.loading

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.data.repository.LlmRecognitionRepository
import com.movie.scanner.data.repository.MovieRepository
import com.movie.scanner.data.repository.TmdbRepository
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.NetworkConnectivityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private enum class BarcodeProcessingOutcome {
    COMPLETED,
    OFFLINE,
    CONTINUE_WITH_COVER,
}

enum class LoadingRetryAction {
    RETRY_TMDB,
    RETAKE_COVER,
    RETRY_CONNECTION,
}

data class LoadingUiState(
    val message: String = "Extracting title from cover image",
    val attempt: Int = 1,
    val showAttempt: Boolean = false,
    val isError: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val retryAction: LoadingRetryAction = LoadingRetryAction.RETRY_TMDB,
)

@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val scanSessionHolder: ScanSessionHolder,
    private val llmRecognitionRepository: LlmRecognitionRepository,
    private val tmdbRepository: TmdbRepository,
    private val movieRepository: MovieRepository,
    private val networkConnectivityChecker: NetworkConnectivityChecker,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoadingUiState())
    val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

    private var coverGuess: MovieGuess? = null
    private var barcodeGuess: MovieGuess? = null
    private var barcodeLookupAttempted = false

    init {
        startProcessing()
    }

    suspend fun loadMoviesForExport(): List<MovieEntity> = movieRepository.listMovies()

    fun retry() {
        when (_uiState.value.retryAction) {
            LoadingRetryAction.RETAKE_COVER -> {
                scanSessionHolder.requestCoverRetake()
            }
            LoadingRetryAction.RETRY_CONNECTION -> startProcessing()
            LoadingRetryAction.RETRY_TMDB -> retryTmdb()
        }
    }

    private fun retryTmdb() {
        if (!networkConnectivityChecker.isConnected()) {
            showOfflineState()
            return
        }
        val title = coverGuess?.title?.takeIf { it.isNotBlank() }
            ?: barcodeGuess?.title?.takeIf { it.isNotBlank() }
        if (title.isNullOrBlank()) {
            showCoverFailure("Could not determine a movie title to search TMDB.")
            return
        }
        val year = coverGuess?.year?.takeIf { it.isNotBlank() }
            ?: barcodeGuess?.year?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            searchTmdbUntilSuccess(title, year, manualRetry = true)
        }
    }

    private fun startProcessing() {
        viewModelScope.launch {
            barcodeLookupAttempted = false
            coverGuess = null
            barcodeGuess = null
            if (!networkConnectivityChecker.isConnected()) {
                showOfflineState()
                return@launch
            }
            if (scanSessionHolder.isManualCoverEntry()) {
                processManualCoverEntry()
                return@launch
            }
            val coverBitmap = scanSessionHolder.coverBitmap
            if (coverBitmap == null) {
                showCoverFailure("Cover photo is missing.")
                return@launch
            }
            val upc = scanSessionHolder.upc
            if (!upc.isNullOrBlank()) {
                when (tryFinishFromBarcode(scanSessionHolder.barcodeBitmap, upc)) {
                    BarcodeProcessingOutcome.COMPLETED,
                    BarcodeProcessingOutcome.OFFLINE,
                    -> return@launch
                    BarcodeProcessingOutcome.CONTINUE_WITH_COVER -> Unit
                }
            }
            processCoverRecognition(coverBitmap)
        }
    }

    private suspend fun tryFinishFromBarcode(
        barcodeBitmap: Bitmap?,
        upc: String,
    ): BarcodeProcessingOutcome {
        barcodeLookupAttempted = true
        _uiState.value = LoadingUiState(message = "Finding with barcode")
        val barcodeResult = llmRecognitionRepository.recognizeBarcode(upc, barcodeBitmap)
        if (!networkConnectivityChecker.isConnected()) {
            showOfflineState()
            return BarcodeProcessingOutcome.OFFLINE
        }
        barcodeGuess = barcodeResult.getOrNull()
        val title = barcodeGuess?.title?.takeIf { title -> title.isNotBlank() }
            ?: return BarcodeProcessingOutcome.CONTINUE_WITH_COVER
        val year = barcodeGuess?.year?.takeIf { year -> year.isNotBlank() }
        val tmdbResults = searchTmdbForBarcodeMatches(title, year)
            ?: return BarcodeProcessingOutcome.OFFLINE
        if (tmdbResults.isEmpty()) {
            return BarcodeProcessingOutcome.CONTINUE_WITH_COVER
        }
        coverGuess = null
        scanSessionHolder.clearBitmaps()
        scanSessionHolder.storeRecognitionResults(
            coverGuessValue = null,
            barcodeGuessValue = barcodeGuess,
            tmdbResults = tmdbResults,
            capturedUpcValue = scanSessionHolder.resolveCapturedUpc(),
        )
        _uiState.update {
            it.copy(
                message = "Done",
                isComplete = true,
                isError = false,
            )
        }
        return BarcodeProcessingOutcome.COMPLETED
    }

    private suspend fun processCoverRecognition(coverBitmap: Bitmap) {
        _uiState.value = LoadingUiState(message = "Extracting title from cover image")
        val coverResult = llmRecognitionRepository.recognizeCover(coverBitmap)
        if (!networkConnectivityChecker.isConnected()) {
            showOfflineState()
            return
        }
        if (coverResult.isFailure) {
            if (isLikelyNetworkFailure(coverResult.exceptionOrNull())) {
                showOfflineState()
            } else {
                showCoverFailure(
                    coverResult.exceptionOrNull()?.message ?: "Could not read the cover image.",
                )
            }
            return
        }
        coverGuess = coverResult.getOrNull()
        val upc = scanSessionHolder.upc
        val barcodeBitmap = scanSessionHolder.barcodeBitmap
        if (!barcodeLookupAttempted && !upc.isNullOrBlank()) {
            barcodeGuess = llmRecognitionRepository.recognizeBarcode(upc, barcodeBitmap).getOrNull()
        }
        scanSessionHolder.clearBitmaps()
        val title = coverGuess?.title?.takeIf { title -> title.isNotBlank() }
            ?: barcodeGuess?.title?.takeIf { title -> title.isNotBlank() }
        if (title.isNullOrBlank()) {
            showCoverFailure("Could not identify a movie title from the cover photo.")
            return
        }
        val year = coverGuess?.year?.takeIf { year -> year.isNotBlank() }
            ?: barcodeGuess?.year?.takeIf { year -> year.isNotBlank() }
        searchTmdbUntilSuccess(title, year, manualRetry = false)
    }

    private suspend fun searchTmdbForBarcodeMatches(
        title: String,
        year: String?,
    ): List<TmdbSearchResult>? {
        if (!networkConnectivityChecker.isConnected()) {
            showOfflineState()
            return null
        }
        var attempt = 1
        val maxAutomaticAttempts = 3
        while (true) {
            _uiState.update {
                it.copy(
                    message = "Searching movie in TMDB",
                    attempt = attempt,
                    showAttempt = attempt > 1,
                    isError = false,
                    errorMessage = null,
                    retryAction = LoadingRetryAction.RETRY_TMDB,
                )
            }
            val result = tmdbRepository.searchMovies(title, year)
            if (result.isSuccess) {
                return result.getOrDefault(emptyList())
            }
            if (!networkConnectivityChecker.isConnected() || isLikelyNetworkFailure(result.exceptionOrNull())) {
                showOfflineState()
                return null
            }
            if (attempt < maxAutomaticAttempts) {
                attempt += 1
                continue
            }
            return emptyList()
        }
    }

    private suspend fun processManualCoverEntry() {
        _uiState.value = LoadingUiState(message = "Searching movie in TMDB")
        val title = scanSessionHolder.manualCoverTitle?.trim().orEmpty()
        if (title.isEmpty()) {
            showCoverFailure("Title is required for manual entry.")
            return
        }
        val year = scanSessionHolder.manualCoverYear?.trim().orEmpty()
        if (year.isEmpty()) {
            showCoverFailure("Year is required for manual entry.")
            return
        }
        val barcodeBitmap = scanSessionHolder.barcodeBitmap
        val upc = scanSessionHolder.upc
        barcodeGuess = if (!upc.isNullOrBlank()) {
            llmRecognitionRepository.recognizeBarcode(upc, barcodeBitmap).getOrNull()
        } else {
            null
        }
        coverGuess = MovieGuess(title = title, year = year)
        scanSessionHolder.clearBitmaps()
        searchTmdbUntilSuccess(
            title = title,
            year = year,
            manualRetry = false,
        )
    }

    private fun showOfflineState() {
        _uiState.update {
            it.copy(
                message = "No internet connection",
                isError = true,
                errorMessage = "Offline scanning is not supported.",
                retryAction = LoadingRetryAction.RETRY_CONNECTION,
                showAttempt = false,
            )
        }
    }

    private fun showCoverFailure(message: String) {
        _uiState.update {
            it.copy(
                message = "Cover not readable",
                isError = true,
                errorMessage = message,
                retryAction = LoadingRetryAction.RETAKE_COVER,
                showAttempt = false,
            )
        }
    }

    private fun isLikelyNetworkFailure(throwable: Throwable?): Boolean {
        if (throwable is IOException) {
            return true
        }
        return !networkConnectivityChecker.isConnected()
    }

    private suspend fun searchTmdbUntilSuccess(
        title: String,
        year: String?,
        manualRetry: Boolean,
    ) {
        if (!networkConnectivityChecker.isConnected()) {
            showOfflineState()
            return
        }
        var attempt = 1
        val maxAutomaticAttempts = 3
        while (true) {
            _uiState.update {
                it.copy(
                    message = "Searching movie in TMDB",
                    attempt = attempt,
                    showAttempt = attempt > 1,
                    isError = false,
                    errorMessage = null,
                    retryAction = LoadingRetryAction.RETRY_TMDB,
                )
            }
            val result = tmdbRepository.searchMovies(title, year)
            if (result.isSuccess) {
                scanSessionHolder.storeRecognitionResults(
                    coverGuessValue = coverGuess,
                    barcodeGuessValue = barcodeGuess,
                    tmdbResults = result.getOrDefault(emptyList()),
                    capturedUpcValue = scanSessionHolder.resolveCapturedUpc(),
                )
                _uiState.update {
                    it.copy(
                        message = "Done",
                        isComplete = true,
                        isError = false,
                    )
                }
                return
            }
            if (!networkConnectivityChecker.isConnected() || isLikelyNetworkFailure(result.exceptionOrNull())) {
                showOfflineState()
                return
            }
            if (!manualRetry && attempt < maxAutomaticAttempts) {
                attempt += 1
                continue
            }
            _uiState.update {
                it.copy(
                    message = "Can't reach TMDB",
                    isError = true,
                    errorMessage = result.exceptionOrNull()?.message ?: "TMDB request failed.",
                    showAttempt = attempt > 1,
                    attempt = attempt,
                    retryAction = LoadingRetryAction.RETRY_TMDB,
                )
            }
            return
        }
    }
}
