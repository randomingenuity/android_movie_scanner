package com.movie.scanner.data.repository

import android.graphics.Bitmap
import com.movie.scanner.data.local.BulkUnprocessedImageDao
import com.movie.scanner.data.model.BulkProcessingResults
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.util.BarcodeDecoder
import com.movie.scanner.util.BulkProcessingResultsJson
import com.movie.scanner.util.NetworkConnectivityChecker
import com.google.mlkit.vision.barcode.BarcodeScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private sealed interface BarcodeRecognitionResult {
    data object Offline : BarcodeRecognitionResult

    data object ContinueWithCover : BarcodeRecognitionResult

    data class Completed(
        val barcodeGuess: MovieGuess,
        val tmdbResults: List<TmdbSearchResult>,
    ) : BarcodeRecognitionResult
}

/**
 * Background worker that recognizes bulk queue items and persists JSON results on each row.
 */
@Singleton
class BulkRecognitionProcessor @Inject constructor(
    private val bulkUnprocessedImageDao: BulkUnprocessedImageDao,
    private val bulkImageRepository: BulkImageRepository,
    private val llmRecognitionRepository: LlmRecognitionRepository,
    private val tmdbRepository: TmdbRepository,
    private val networkConnectivityChecker: NetworkConnectivityChecker,
) {
    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val barcodeScanner: BarcodeScanner = BarcodeDecoder.buildBarcodeScanner()
    private val _recognizingRecordIds = MutableStateFlow<Set<Long>>(emptySet())
    val recognizingRecordIds: StateFlow<Set<Long>> = _recognizingRecordIds.asStateFlow()
    private var processorJobActive = false

    init {
        scheduleRecognition()
    }

    /**
     * Ensures the background queue drains unprocessed rows that lack stored recognition results.
     */
    fun scheduleRecognition() {
        processorScope.launch {
            queueMutex.withLock {
                if (processorJobActive) {
                    return@launch
                }
                processorJobActive = true
            }
            try {
                drainRecognitionQueue()
            } finally {
                queueMutex.withLock {
                    processorJobActive = false
                }
                if (bulkUnprocessedImageDao.listUnrecognizedOrderedById().isNotEmpty()) {
                    scheduleRecognition()
                }
            }
        }
    }

    private suspend fun drainRecognitionQueue() {
        while (true) {
            val nextRecord = bulkUnprocessedImageDao.listUnrecognizedOrderedById().firstOrNull()
                ?: return
            recognizeRecord(nextRecord)
        }
    }

    private suspend fun recognizeRecord(record: BulkUnprocessedImageEntity) {
        val recordId = record.id
        markRecognizing(recordId, recognizing = true)
        try {
            val results = runRecognition(record)
            bulkUnprocessedImageDao.updateProcessingResultsJson(
                recordId = recordId,
                processingResultsJson = BulkProcessingResultsJson.encode(results),
            )
        } catch (exception: Exception) {
            val message = exception.message ?: "Could not recognize this item."
            bulkUnprocessedImageDao.updateProcessingResultsJson(
                recordId = recordId,
                processingResultsJson = BulkProcessingResultsJson.encode(
                    BulkProcessingResults(recognitionError = message),
                ),
            )
        } finally {
            markRecognizing(recordId, recognizing = false)
        }
    }

    private suspend fun runRecognition(record: BulkUnprocessedImageEntity): BulkProcessingResults {
        if (!networkConnectivityChecker.isConnected()) {
            return BulkProcessingResults(recognitionError = "Offline scanning is not supported.")
        }
        val barcodeBitmap = bulkImageRepository.loadBitmap(record.barcodeRelFilepath)
        val coverBitmap = bulkImageRepository.loadBitmap(record.coverRelFilepath)
        if (barcodeBitmap == null || coverBitmap == null) {
            return BulkProcessingResults(recognitionError = "Saved images could not be loaded.")
        }
        return try {
            recognizeLoadedPair(
                barcodeBitmap = barcodeBitmap,
                coverBitmap = coverBitmap,
            )
        } finally {
            if (!barcodeBitmap.isRecycled) {
                barcodeBitmap.recycle()
            }
            if (!coverBitmap.isRecycled) {
                coverBitmap.recycle()
            }
        }
    }

    private suspend fun recognizeLoadedPair(
        barcodeBitmap: Bitmap,
        coverBitmap: Bitmap,
    ): BulkProcessingResults {
        val decodedBarcode = withContext(Dispatchers.Default) {
            BarcodeDecoder.decodeFromBitmap(barcodeBitmap, barcodeScanner)
        }
        var coverGuess: MovieGuess? = null
        var barcodeGuess: MovieGuess? = null
        var barcodeLookupAttempted = false
        val capturedUpc = decodedBarcode?.takeIf { upc -> upc.isNotBlank() }
        if (!capturedUpc.isNullOrBlank()) {
            when (val barcodeOutcome = tryFinishFromBarcode(capturedUpc, barcodeBitmap)) {
                BarcodeRecognitionResult.Offline ->
                    return BulkProcessingResults(recognitionError = "Offline scanning is not supported.")
                is BarcodeRecognitionResult.Completed -> {
                    return BulkProcessingResults(
                        coverGuess = null,
                        barcodeGuess = barcodeOutcome.barcodeGuess,
                        tmdbResults = barcodeOutcome.tmdbResults,
                        capturedUpc = capturedUpc,
                    )
                }
                BarcodeRecognitionResult.ContinueWithCover -> {
                    barcodeGuess = llmRecognitionRepository.recognizeBarcode(capturedUpc, barcodeBitmap).getOrNull()
                }
            }
            barcodeLookupAttempted = true
        }
        val coverResult = llmRecognitionRepository.recognizeCover(coverBitmap)
        if (!networkConnectivityChecker.isConnected()) {
            return BulkProcessingResults(recognitionError = "Offline scanning is not supported.")
        }
        if (coverResult.isFailure) {
            val message = if (isLikelyNetworkFailure(coverResult.exceptionOrNull())) {
                "Offline scanning is not supported."
            } else {
                coverResult.exceptionOrNull()?.message ?: "Could not read the cover image."
            }
            return BulkProcessingResults(
                barcodeGuess = barcodeGuess,
                capturedUpc = capturedUpc,
                recognitionError = message,
            )
        }
        coverGuess = coverResult.getOrNull()
        if (!barcodeLookupAttempted && !capturedUpc.isNullOrBlank()) {
            barcodeGuess = llmRecognitionRepository.recognizeBarcode(capturedUpc, barcodeBitmap).getOrNull()
        }
        val title = coverGuess?.title?.takeIf { value -> value.isNotBlank() }
            ?: barcodeGuess?.title?.takeIf { value -> value.isNotBlank() }
        if (title.isNullOrBlank()) {
            return BulkProcessingResults(
                coverGuess = coverGuess,
                barcodeGuess = barcodeGuess,
                capturedUpc = capturedUpc,
                recognitionError = "Could not identify a movie title from the cover photo.",
            )
        }
        val year = coverGuess?.year?.takeIf { value -> value.isNotBlank() }
            ?: barcodeGuess?.year?.takeIf { value -> value.isNotBlank() }
        val tmdbResults = searchTmdbUntilSuccess(title, year)
            ?: return BulkProcessingResults(
                coverGuess = coverGuess,
                barcodeGuess = barcodeGuess,
                capturedUpc = capturedUpc,
                recognitionError = "Offline scanning is not supported.",
            )
        return BulkProcessingResults(
            coverGuess = coverGuess,
            barcodeGuess = barcodeGuess,
            tmdbResults = tmdbResults,
            capturedUpc = capturedUpc,
        )
    }

    private suspend fun tryFinishFromBarcode(
        upc: String,
        barcodeBitmap: Bitmap,
    ): BarcodeRecognitionResult {
        val barcodeResult = llmRecognitionRepository.recognizeBarcode(upc, barcodeBitmap)
        if (!networkConnectivityChecker.isConnected()) {
            return BarcodeRecognitionResult.Offline
        }
        val guess = barcodeResult.getOrNull()
        val title = guess?.title?.takeIf { value -> value.isNotBlank() }
            ?: return BarcodeRecognitionResult.ContinueWithCover
        val year = guess.year.takeIf { value -> value.isNotBlank() }
        val tmdbResults = searchTmdbForBarcodeMatches(title, year)
            ?: return BarcodeRecognitionResult.Offline
        if (tmdbResults.isEmpty()) {
            return BarcodeRecognitionResult.ContinueWithCover
        }
        return BarcodeRecognitionResult.Completed(
            barcodeGuess = guess,
            tmdbResults = tmdbResults,
        )
    }

    private suspend fun searchTmdbForBarcodeMatches(
        title: String,
        year: String?,
    ): List<TmdbSearchResult>? {
        if (!networkConnectivityChecker.isConnected()) {
            return null
        }
        var attempt = 1
        val maxAutomaticAttempts = 3
        while (true) {
            val result = tmdbRepository.searchMovies(title, year)
            if (result.isSuccess) {
                return result.getOrDefault(emptyList())
            }
            if (!networkConnectivityChecker.isConnected() || isLikelyNetworkFailure(result.exceptionOrNull())) {
                return null
            }
            if (attempt < maxAutomaticAttempts) {
                attempt += 1
                continue
            }
            return emptyList()
        }
    }

    private suspend fun searchTmdbUntilSuccess(
        title: String,
        year: String?,
    ): List<TmdbSearchResult>? {
        if (!networkConnectivityChecker.isConnected()) {
            return null
        }
        var attempt = 1
        val maxAutomaticAttempts = 3
        while (true) {
            val result = tmdbRepository.searchMovies(title, year)
            if (result.isSuccess) {
                return result.getOrDefault(emptyList())
            }
            if (!networkConnectivityChecker.isConnected() || isLikelyNetworkFailure(result.exceptionOrNull())) {
                return null
            }
            if (attempt < maxAutomaticAttempts) {
                attempt += 1
                continue
            }
            return emptyList()
        }
    }

    private fun isLikelyNetworkFailure(throwable: Throwable?): Boolean {
        if (throwable is IOException) {
            return true
        }
        return !networkConnectivityChecker.isConnected()
    }

    private fun markRecognizing(recordId: Long, recognizing: Boolean) {
        _recognizingRecordIds.update { currentIds ->
            if (recognizing) {
                currentIds + recordId
            } else {
                currentIds - recordId
            }
        }
    }
}
