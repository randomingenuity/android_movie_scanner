package com.movie.scanner.util

import com.movie.scanner.data.model.MovieEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CsvExporter {
    fun buildDefaultFilename(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "${timestamp}_movies.csv"
    }

    fun buildCsv(movies: List<MovieEntity>): String {
        val header = "title,year,barcode,tmdb_url,tmdb_id,poster_url"
        val rows = movies.map { movie ->
            listOf(
                movie.title.toCsvField(),
                movie.year.toCsvField(),
                movie.upc.orEmpty().toCsvField(),
                movie.tmdbUrl.orEmpty().toCsvField(),
                movie.tmdbId?.toString().orEmpty().toCsvField(),
                movie.posterUrl.orEmpty().toCsvField(),
            ).joinToString(",")
        }
        val csvBody = (listOf(header) + rows).joinToString("\n")
        return "\uFEFF$csvBody"
    }

    private fun String.toCsvField(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
