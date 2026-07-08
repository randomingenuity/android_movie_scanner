package com.movie.scanner.data.session

import android.graphics.Bitmap
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieGuess
import com.movie.scanner.data.model.TmdbSearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanSessionHolder @Inject constructor() {
    var coverBitmap: Bitmap? = null
        private set
    var barcodeBitmap: Bitmap? = null
        private set
    var upc: String? = null
        private set
    var capturedUpc: String? = null
        private set
    var barcodeSkipped: Boolean = false
        private set
    var manualCoverEntry: Boolean = false
        private set
    var manualCoverTitle: String? = null
        private set
    var manualCoverYear: String? = null
        private set

    var coverGuess: MovieGuess? = null
        private set
    var barcodeGuess: MovieGuess? = null
        private set
    var initialTmdbResults: List<TmdbSearchResult> = emptyList()
        private set
    var lastAddedTitle: String? = null
        private set
    var lastReviewFeatureType: FeatureType = FeatureType.MOVIE
        private set
    var lastReviewLocation: String = ""
        private set
    private var coverRetakeRequested: Boolean = false
    var isBulkProcessing: Boolean = false
        private set
    var currentBulkRecordId: Long? = null
        private set
    var bulkCoverRelFilepath: String? = null
        private set
    var bulkProcessingStopRequested: Boolean = false
        private set
    private var bulkRescanRecordId: Long? = null
    private var bulkQueueResumePending: Boolean = false

    fun signalBulkQueueResume() {
        bulkQueueResumePending = true
    }

    fun consumeBulkQueueResume(): Boolean {
        val resumePending = bulkQueueResumePending
        bulkQueueResumePending = false
        return resumePending
    }

    /**
     * Marks the active review session as part of a bulk queue item.
     */
    fun startBulkItem(recordId: Long, coverRelFilepath: String) {
        isBulkProcessing = true
        currentBulkRecordId = recordId
        bulkCoverRelFilepath = coverRelFilepath
        bulkProcessingStopRequested = false
    }

    fun requestStopBulkProcessing() {
        bulkProcessingStopRequested = true
    }

    /**
     * Marks the next bulk capture session as replacing images for an existing queue row.
     */
    fun beginBulkRescan(recordId: Long) {
        bulkRescanRecordId = recordId
    }

    fun resolveBulkRescanRecordId(): Long? = bulkRescanRecordId

    fun clearBulkRescan() {
        bulkRescanRecordId = null
    }

    fun finishBulkItem() {
        isBulkProcessing = false
        currentBulkRecordId = null
        bulkCoverRelFilepath = null
    }

    fun consumeBulkProcessingStopRequested(): Boolean {
        val stopRequested = bulkProcessingStopRequested
        bulkProcessingStopRequested = false
        return stopRequested
    }

    fun recordBarcodeCaptureForBulk(bitmap: Bitmap) {
        barcodeBitmap = bitmap
        upc = null
        capturedUpc = null
        barcodeSkipped = false
    }

    fun rememberReviewFeatureType(featureType: FeatureType) {
        lastReviewFeatureType = featureType
    }

    fun rememberReviewLocation(location: String) {
        lastReviewLocation = location
    }

    fun startNewScan() {
        clearBitmaps()
        clearCapturedUpc()
        barcodeSkipped = false
        clearManualCoverEntry()
        coverGuess = null
        barcodeGuess = null
        initialTmdbResults = emptyList()
        coverRetakeRequested = false
    }

    fun requestCoverRetake() {
        coverBitmap?.recycle()
        coverBitmap = null
        clearManualCoverEntry()
        coverRetakeRequested = true
    }

    fun consumeCoverRetakeRequest(): Boolean {
        val requested = coverRetakeRequested
        coverRetakeRequested = false
        return requested
    }

    fun recordBarcodeCapture(upcValue: String, bitmap: Bitmap) {
        upc = upcValue
        capturedUpc = upcValue
        barcodeBitmap = bitmap
        barcodeSkipped = false
    }

    fun skipBarcode() {
        upc = null
        capturedUpc = null
        barcodeBitmap = null
        barcodeSkipped = true
    }

    fun recordCoverCapture(bitmap: Bitmap) {
        clearManualCoverEntry()
        coverBitmap = bitmap
    }

    fun recordManualCoverEntry(title: String, year: String) {
        coverBitmap?.recycle()
        coverBitmap = null
        manualCoverEntry = true
        manualCoverTitle = title
        manualCoverYear = year
    }

    fun isManualCoverEntry(): Boolean = manualCoverEntry

    fun storeRecognitionResults(
        coverGuessValue: MovieGuess?,
        barcodeGuessValue: MovieGuess?,
        tmdbResults: List<TmdbSearchResult>,
        capturedUpcValue: String? = upc,
    ) {
        coverGuess = coverGuessValue
        barcodeGuess = barcodeGuessValue
        initialTmdbResults = tmdbResults
        if (!capturedUpcValue.isNullOrBlank()) {
            capturedUpc = capturedUpcValue
            upc = capturedUpcValue
        }
    }

    fun resolveCapturedUpc(): String? = capturedUpc?.takeIf { it.isNotBlank() } ?: upc?.takeIf { it.isNotBlank() }

    fun clearBitmaps() {
        coverBitmap?.recycle()
        barcodeBitmap?.recycle()
        coverBitmap = null
        barcodeBitmap = null
    }

    fun finishScan(addedTitle: String? = null) {
        clearBitmaps()
        clearCapturedUpc()
        barcodeSkipped = false
        clearManualCoverEntry()
        coverGuess = null
        barcodeGuess = null
        initialTmdbResults = emptyList()
        lastAddedTitle = addedTitle
    }

    fun consumeLastAddedTitle(): String? {
        val title = lastAddedTitle
        lastAddedTitle = null
        return title
    }

    private fun clearManualCoverEntry() {
        manualCoverEntry = false
        manualCoverTitle = null
        manualCoverYear = null
    }

    private fun clearCapturedUpc() {
        upc = null
        capturedUpc = null
    }
}
