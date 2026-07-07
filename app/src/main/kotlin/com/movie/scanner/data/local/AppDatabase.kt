package com.movie.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.model.MovieEntity

@Database(
    entities = [
        MovieEntity::class,
        BulkUnprocessedImageEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao

    abstract fun bulkUnprocessedImageDao(): BulkUnprocessedImageDao
}
