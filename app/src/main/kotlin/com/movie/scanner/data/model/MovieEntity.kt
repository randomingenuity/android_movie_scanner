package com.movie.scanner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val year: String,
    val tmdbId: Int?,
    val tmdbUrl: String?,
    val posterUrl: String?,
    val upc: String?,
    val isForceAdded: Boolean,
    val sortOrder: Int,
    val featureType: String = FeatureType.MOVIE.label,
    val discType: String? = null,
    val location: String? = null,
    val seasonNumber: Int? = null,
    val numberOfDiscs: Int? = null,
)
