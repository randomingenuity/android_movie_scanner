package com.movie.scanner.util

import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CsvExporter {
    fun buildDefaultFilename(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "${timestamp}_catalog.csv"
    }

    fun buildDefaultZipFilename(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "${timestamp}_catalog.zip"
    }

    fun buildCsv(
        movies: List<MovieEntity>,
        includeCoverFilename: Boolean = false,
        coverFileExists: (String?) -> Boolean = { coverRelFilepath -> !coverRelFilepath.isNullOrBlank() },
    ): String {
        val baseHeader =
            "title,year,feature_type,barcode,disc_type,movie_release_type,location,season_number,number_of_discs,tmdb_url,tmdb_id,poster_url,above_the_title_actor_names_matched,above_the_title_actor_tmdb_ids_matched,above_the_title_actors_unmatched"
        val header = if (includeCoverFilename) {
            "$baseHeader,cover_filename"
        } else {
            baseHeader
        }
        val rows = movies.map { movie ->
            val featureType = FeatureType.fromLabel(movie.featureType)
            val fields = listOf(
                movie.title.toCsvField(),
                movie.year.toCsvField(),
                movie.featureType.toCsvField(),
                movie.upc.orEmpty().toCsvField(),
                movie.discType.orEmpty().toCsvField(),
                if (featureType == FeatureType.MOVIE) {
                    movie.edition.orEmpty().toCsvField()
                } else {
                    "".toCsvField()
                },
                movie.location.orEmpty().toCsvField(),
                if (featureType == FeatureType.TV) {
                    movie.seasonNumber?.toString().orEmpty().toCsvField()
                } else {
                    "".toCsvField()
                },
                movie.numberOfDiscs?.toString().orEmpty().toCsvField(),
                movie.tmdbUrl.orEmpty().toCsvField(),
                movie.tmdbId?.toString().orEmpty().toCsvField(),
                movie.posterUrl.orEmpty().toCsvField(),
                movie.aboveTheTitleActorNamesMatched.orEmpty().toCsvField(),
                movie.aboveTheTitleActorTmdbIdsMatched.orEmpty().toCsvField(),
                movie.aboveTheTitleActorsUnmatched.orEmpty().toCsvField(),
            )
            val coverFilenameFields = if (includeCoverFilename) {
                val coverRelFilepath = movie.coverRelFilepath
                val coverFilename = if (coverFileExists(coverRelFilepath)) {
                    ListCoverFilename.buildCoverFilenameForCsv(coverRelFilepath)
                } else {
                    ""
                }
                listOf(coverFilename.toCsvField())
            } else {
                emptyList()
            }
            (fields + coverFilenameFields).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun String.toCsvField(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
