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

    fun buildCsv(movies: List<MovieEntity>): String {
        val header = "title,year,feature_type,barcode,disc_type,location,season_number,number_of_discs,tmdb_url,tmdb_id,poster_url"
        val rows = movies.map { movie ->
            listOf(
                movie.title.toCsvField(),
                movie.year.toCsvField(),
                movie.featureType.toCsvField(),
                movie.upc.orEmpty().toCsvField(),
                movie.discType.orEmpty().toCsvField(),
                movie.location.orEmpty().toCsvField(),
                if (FeatureType.fromLabel(movie.featureType) == FeatureType.TV) {
                    movie.seasonNumber?.toString().orEmpty().toCsvField()
                } else {
                    "".toCsvField()
                },
                movie.numberOfDiscs?.toString().orEmpty().toCsvField(),
                movie.tmdbUrl.orEmpty().toCsvField(),
                movie.tmdbId?.toString().orEmpty().toCsvField(),
                movie.posterUrl.orEmpty().toCsvField(),
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun String.toCsvField(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
