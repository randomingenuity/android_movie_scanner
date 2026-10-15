package com.movie.scanner.util

import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.ListCoverRepository
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CatalogZipExporter {
    fun buildZipFile(
        exportDirectory: File,
        csvFilename: String,
        csvContent: String,
        movies: List<MovieEntity>,
        listCoverRepository: ListCoverRepository,
    ): File {
        exportDirectory.mkdirs()
        val zipFile = File(exportDirectory, CsvExporter.buildDefaultZipFilename())
        ZipOutputStream(zipFile.outputStream()).use { zipOutputStream ->
            zipOutputStream.putNextEntry(ZipEntry(csvFilename))
            zipOutputStream.write(csvContent.toByteArray(Charsets.UTF_8))
            zipOutputStream.closeEntry()
            for (movie in movies) {
                val coverRelFilepath = movie.coverRelFilepath
                if (coverRelFilepath.isNullOrBlank() || !listCoverRepository.coverFileExists(coverRelFilepath)) {
                    continue
                }
                val coverFile = listCoverRepository.resolveAbsolutePath(coverRelFilepath)
                val zipEntryPath = ListCoverFilename.buildZipEntryPath(coverRelFilepath)
                zipOutputStream.putNextEntry(ZipEntry(zipEntryPath))
                FileInputStream(coverFile).use { inputStream ->
                    inputStream.copyTo(zipOutputStream)
                }
                zipOutputStream.closeEntry()
            }
        }
        return zipFile
    }
}
