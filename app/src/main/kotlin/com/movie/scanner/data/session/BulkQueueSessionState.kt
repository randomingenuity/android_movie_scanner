package com.movie.scanner.data.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared bulk-queue processing flags used by the queue screen and review preloading.
 */
@Singleton
class BulkQueueSessionState @Inject constructor() {
    var shouldContinueProcessing: Boolean = false
    val deferredRecordIds: MutableSet<Long> = mutableSetOf()
    var processingRecordId: Long? = null
    var lastAddedMovieId: Long? = null
        private set
    var lastProcessedBulkRecordId: Long? = null
        private set

    /**
     * Clears deferred rows and processing id when the operator starts a new Process run.
     */
    fun resetForNewProcessingRun() {
        deferredRecordIds.clear()
        processingRecordId = null
        clearLastAddedEntry()
    }

    /**
     * Remembers the list row and bulk queue row from the most recent successful add during processing.
     */
    fun rememberLastAddedEntry(movieId: Long, bulkRecordId: Long?) {
        lastAddedMovieId = movieId
        lastProcessedBulkRecordId = bulkRecordId
    }

    /**
     * Drops the remembered last-added entry after navigating back to edit it.
     */
    fun clearLastAddedEntry() {
        lastAddedMovieId = null
        lastProcessedBulkRecordId = null
    }

    /**
     * Skips a queue row for the current processing pass without marking it processed.
     */
    fun deferRecord(recordId: Long) {
        deferredRecordIds.add(recordId)
    }
}
