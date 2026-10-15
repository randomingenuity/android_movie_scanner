package com.movie.scanner.util

import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.data.repository.ListCoverRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

class CatalogZipExporterTest {
    @Test
    fun buildZipFile_includesCsvAndCoverEntries() {
        val exportDirectory = File.createTempFile("catalog-export", "").apply {
            delete()
            mkdirs()
        }
        val coverFile = File(exportDirectory, "603.jpg")
        coverFile.writeBytes(byteArrayOf(1, 2, 3))
        val listCoverRepository = mockk<ListCoverRepository>()
        every { listCoverRepository.coverFileExists("603.jpg") } returns true
        every { listCoverRepository.resolveAbsolutePath("603.jpg") } returns coverFile
        val movies = listOf(
            MovieEntity(
                title = "The Matrix",
                year = "1999",
                tmdbId = 603,
                tmdbUrl = null,
                posterUrl = null,
                upc = null,
                isForceAdded = false,
                sortOrder = 0,
                coverRelFilepath = "603.jpg",
            ),
        )
        val csvFilename = "20260101-1200_catalog.csv"
        val csvContent = "title\n\"The Matrix\""

        val zipFile = CatalogZipExporter.buildZipFile(
            exportDirectory = exportDirectory,
            csvFilename = csvFilename,
            csvContent = csvContent,
            movies = movies,
            listCoverRepository = listCoverRepository,
        )

        assertTrue(zipFile.isFile)
        val entryNames = mutableListOf<String>()
        ZipInputStream(zipFile.inputStream()).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zipInputStream.nextEntry
            }
        }
        assertTrue(entryNames.contains(csvFilename))
        assertTrue(entryNames.contains("covers/603.jpg"))
        exportDirectory.deleteRecursively()
    }
}
