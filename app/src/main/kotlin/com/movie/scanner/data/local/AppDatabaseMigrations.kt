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

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE images_bulk_unprocessed ADD COLUMN processing_results_json TEXT",
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("UPDATE movies SET discType = 'bluray' WHERE discType = 'Blu-Ray'")
        database.execSQL("UPDATE movies SET discType = 'dvd' WHERE discType = 'DVD'")
        database.execSQL("UPDATE movies SET discType = '4k_bluray' WHERE discType = '4K Blu-Ray'")
        database.execSQL("UPDATE movies SET discType = '3d_bluray' WHERE discType = '3D Blu-Ray'")
        database.execSQL("UPDATE movies SET discType = 'hd_dvd' WHERE discType = 'HD DVD'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE movies ADD COLUMN edition TEXT")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE movies ADD COLUMN aboveTheTitle TEXT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE movies ADD COLUMN aboveTheTitleActorsMatched TEXT")
        database.execSQL("ALTER TABLE movies ADD COLUMN aboveTheTitleActorsUnmatched TEXT")
        database.execSQL(
            """
            UPDATE movies
            SET aboveTheTitleActorsUnmatched = aboveTheTitle
            WHERE aboveTheTitle IS NOT NULL AND TRIM(aboveTheTitle) != ''
            """.trimIndent(),
        )
        rebuildMoviesTableWithoutLegacyAboveTheTitleColumn(database)
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        rebuildMoviesTableWithoutLegacyAboveTheTitleColumn(database)
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE movies RENAME COLUMN aboveTheTitleActorsMatched TO aboveTheTitleActorNamesMatched",
        )
        database.execSQL("ALTER TABLE movies ADD COLUMN aboveTheTitleActorTmdbIdsMatched TEXT")
    }
}

private fun rebuildMoviesTableWithoutLegacyAboveTheTitleColumn(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS movies_migration_new (
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
            edition TEXT,
            location TEXT,
            seasonNumber INTEGER,
            numberOfDiscs INTEGER,
            aboveTheTitleActorsMatched TEXT,
            aboveTheTitleActorsUnmatched TEXT
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO movies_migration_new (
            id, title, year, tmdbId, tmdbUrl, posterUrl, upc,
            isForceAdded, sortOrder, featureType, discType, edition,
            location, seasonNumber, numberOfDiscs,
            aboveTheTitleActorsMatched, aboveTheTitleActorsUnmatched
        )
        SELECT
            id, title, year, tmdbId, tmdbUrl, posterUrl, upc,
            isForceAdded, sortOrder, featureType, discType, edition,
            location, seasonNumber, numberOfDiscs,
            aboveTheTitleActorsMatched, aboveTheTitleActorsUnmatched
        FROM movies
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE movies")
    database.execSQL("ALTER TABLE movies_migration_new RENAME TO movies")
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
