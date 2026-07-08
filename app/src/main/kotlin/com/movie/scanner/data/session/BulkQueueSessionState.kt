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

    /**
     * Clears deferred rows and processing id when the operator starts a new Process run.
     */
    fun resetForNewProcessingRun() {
        deferredRecordIds.clear()
        processingRecordId = null
    }

    /**
     * Skips a queue row for the current processing pass without marking it processed.
     */
    fun deferRecord(recordId: Long) {
        deferredRecordIds.add(recordId)
    }
}
