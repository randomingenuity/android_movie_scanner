package com.movie.scanner.data.repository

import android.content.Context
import com.movie.scanner.util.CatalogExportFormat
import com.movie.scanner.util.CatalogZipExporter
import com.movie.scanner.util.CsvExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class CatalogExportPayload {
    data class Csv(val content: String, val filename: String) : CatalogExportPayload()

    data class Zip(val file: File, val filename: String) : CatalogExportPayload()
}

@Singleton
class CatalogExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val movieRepository: MovieRepository,
    private val listCoverRepository: ListCoverRepository,
) {
    suspend fun buildExport(format: CatalogExportFormat): CatalogExportPayload = withContext(Dispatchers.IO) {
        val movies = movieRepository.listMovies()
        val csvFilename = CsvExporter.buildDefaultFilename()
        when (format) {
            CatalogExportFormat.CsvOnly -> CatalogExportPayload.Csv(
                content = CsvExporter.buildCsv(movies),
                filename = csvFilename,
            )
            CatalogExportFormat.ZipWithCovers -> {
                val csvContent = CsvExporter.buildCsv(
                    movies = movies,
                    includeCoverFilename = true,
                    coverFileExists = listCoverRepository::coverFileExists,
                )
                val exportDirectory = File(context.cacheDir, "exports")
                val zipFile = CatalogZipExporter.buildZipFile(
                    exportDirectory = exportDirectory,
                    csvFilename = csvFilename,
                    csvContent = csvContent,
                    movies = movies,
                    listCoverRepository = listCoverRepository,
                )
                CatalogExportPayload.Zip(
                    file = zipFile,
                    filename = CsvExporter.buildDefaultZipFilename(),
                )
            }
        }
    }
}
