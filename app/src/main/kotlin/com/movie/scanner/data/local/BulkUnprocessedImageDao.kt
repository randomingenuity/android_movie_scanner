package com.movie.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BulkUnprocessedImageDao {
    @Query("SELECT * FROM images_bulk_unprocessed ORDER BY id ASC")
    fun observeAllOrderedById(): Flow<List<BulkUnprocessedImageEntity>>

    @Query("SELECT * FROM images_bulk_unprocessed ORDER BY id ASC")
    suspend fun listAllOrderedById(): List<BulkUnprocessedImageEntity>

    @Query(
        """
        SELECT * FROM images_bulk_unprocessed
        WHERE was_processed = 0
        ORDER BY id ASC
        """,
    )
    suspend fun listUnprocessedOrderedById(): List<BulkUnprocessedImageEntity>

    @Query("SELECT COUNT(*) FROM images_bulk_unprocessed WHERE was_processed = 0")
    fun observeUnprocessedRecordCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BulkUnprocessedImageEntity): Long

    @Query("UPDATE images_bulk_unprocessed SET was_processed = 1 WHERE id = :recordId")
    suspend fun markProcessed(recordId: Long)

    @Query("SELECT * FROM images_bulk_unprocessed WHERE id = :recordId")
    suspend fun getById(recordId: Long): BulkUnprocessedImageEntity?

    @Query("DELETE FROM images_bulk_unprocessed WHERE id = :recordId")
    suspend fun deleteById(recordId: Long)

    @Query(
        """
        SELECT * FROM images_bulk_unprocessed
        WHERE was_processed = 1
        ORDER BY id ASC
        """,
    )
    suspend fun listProcessedOrderedById(): List<BulkUnprocessedImageEntity>

    @Query("DELETE FROM images_bulk_unprocessed WHERE was_processed = 1")
    suspend fun deleteAllProcessed()
}
