package com.movie.scanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.movie.scanner.data.model.MovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY sortOrder ASC")
    fun observeMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies ORDER BY sortOrder ASC")
    suspend fun listMovies(): List<MovieEntity>

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun countMovies(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM movies WHERE tmdbId = :tmdbId)")
    suspend fun existsByTmdbId(tmdbId: Int): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM movies
            WHERE lower(trim(title)) = lower(trim(:title))
            AND year = :year
        )
        """,
    )
    suspend fun existsByTitleAndYear(title: String, year: String): Boolean

    @Query("SELECT * FROM movies WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun findByTmdbId(tmdbId: Int): MovieEntity?

    @Query(
        """
        SELECT * FROM movies
        WHERE lower(trim(title)) = lower(trim(:title))
        AND year = :year
        LIMIT 1
        """,
    )
    suspend fun findByTitleAndYear(title: String, year: String): MovieEntity?

    @Query("SELECT MAX(sortOrder) FROM movies")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movie: MovieEntity): Long

    @Update
    suspend fun update(movie: MovieEntity)

    @Query("DELETE FROM movies WHERE id = :movieId")
    suspend fun deleteById(movieId: Long)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()
}
