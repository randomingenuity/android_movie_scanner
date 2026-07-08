package com.movie.scanner.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a barcode/cover image pair captured during bulk scanning before TMDB review.
 */
@Entity(tableName = "images_bulk_unprocessed")
data class BulkUnprocessedImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at_timestamp")
    val createdAtTimestamp: Long,
    @ColumnInfo(name = "barcode_rel_filepath")
    val barcodeRelFilepath: String,
    @ColumnInfo(name = "cover_rel_filepath")
    val coverRelFilepath: String,
    @ColumnInfo(name = "was_processed")
    val wasProcessed: Boolean = false,
    @ColumnInfo(name = "processing_results_json")
    val processingResultsJson: String? = null,
)
