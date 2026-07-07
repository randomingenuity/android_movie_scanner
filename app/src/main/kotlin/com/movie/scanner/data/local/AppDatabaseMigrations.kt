package com.movie.scanner.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.movie.scanner.data.model.FeatureType

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE movies ADD COLUMN mediaMode TEXT NOT NULL DEFAULT '${FeatureType.MOVIE.label}'",
        )
        database.execSQL("ALTER TABLE movies ADD COLUMN discType TEXT")
        database.execSQL("ALTER TABLE movies ADD COLUMN location TEXT")
        database.execSQL("ALTER TABLE movies ADD COLUMN durationMinutes INTEGER")
        database.execSQL("ALTER TABLE movies ADD COLUMN seasonNumber INTEGER")
        database.execSQL("ALTER TABLE movies ADD COLUMN numberOfDiscs INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movies_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                year TEXT NOT NULL,
                tmdbId INTEGER,
                tmdbUrl TEXT,
                posterUrl TEXT,
                upc TEXT,
                isForceAdded INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                mediaMode TEXT NOT NULL,
                discType TEXT,
                location TEXT,
                seasonNumber INTEGER,
                numberOfDiscs INTEGER
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO movies_new (
                id, title, year, tmdbId, tmdbUrl, posterUrl, upc,
                isForceAdded, sortOrder, mediaMode, discType, location,
                seasonNumber, numberOfDiscs
            )
            SELECT
                id, title, year, tmdbId, tmdbUrl, posterUrl, upc,
                isForceAdded, sortOrder, mediaMode, discType, location,
                seasonNumber, numberOfDiscs
            FROM movies
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE movies")
        database.execSQL("ALTER TABLE movies_new RENAME TO movies")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS images_bulk_unprocessed (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                created_at_timestamp INTEGER NOT NULL,
                barcode_rel_filepath TEXT NOT NULL,
                cover_rel_filepath TEXT NOT NULL,
                was_processed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movies_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                year TEXT NOT NULL,
                tmdbId INTEGER,
                tmdbUrl TEXT,
                posterUrl TEXT,
                upc TEXT,
                isForceAdded INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                featureType TEXT NOT NULL,
                discType TEXT,
                location TEXT,
                seasonNumber INTEGER,
                numberOfDiscs INTEGER
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO movies_new (
                id, title, year, tmdbId, tmdbUrl, posterUrl, upc,
                isForceAdded, sortOrder, featureType, discType, location,
                seasonNumber, numberOfDiscs
            )
            SELECT
                id, title, year, tmdbId, tmdbUrl, posterUrl, upc,
                isForceAdded, sortOrder, mediaMode, discType, location,
                seasonNumber, numberOfDiscs
            FROM movies
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE movies")
        database.execSQL("ALTER TABLE movies_new RENAME TO movies")
    }
}
