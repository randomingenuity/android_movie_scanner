package com.movie.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.movie.scanner.data.model.MovieEntity

@Database(
    entities = [MovieEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
}
