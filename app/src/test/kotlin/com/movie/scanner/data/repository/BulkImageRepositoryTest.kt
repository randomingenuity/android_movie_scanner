package com.movie.scanner.data.repository

import android.content.Context
import com.movie.scanner.data.local.BulkUnprocessedImageDao
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.inject.Provider

class BulkImageRepositoryTest {
    private val bulkUnprocessedImageDao = mockk<BulkUnprocessedImageDao>(relaxed = true)
    private val bulkRecognitionProcessorProvider = mockk<Provider<BulkRecognitionProcessor>>(relaxed = true)
    private val context = mockk<Context>()
    private lateinit var filesDirectory: File
    private lateinit var bulkImageRepository: BulkImageRepository

    @Before
    fun setUp() {
        filesDirectory = File.createTempFile("bulk-image-repository-test", "").apply {
            delete()
            mkdirs()
        }
        every { context.filesDir } returns filesDirectory
        bulkImageRepository = BulkImageRepository(
            context = context,
            bulkUnprocessedImageDao = bulkUnprocessedImageDao,
            bulkRecognitionProcessorProvider = bulkRecognitionProcessorProvider,
        )
    }

    @After
    fun tearDown() {
        filesDirectory.deleteRecursively()
    }

    @Test
    fun deleteRecord_removesBarcodeAndCoverFilesFromDisk() = runTest {
        val record = BulkUnprocessedImageEntity(
            id = 7L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = "barcode_100.jpg",
            coverRelFilepath = "cover_100.jpg",
        )
        val barcodeFile = writeBulkImage("barcode_100.jpg")
        val coverFile = writeBulkImage("cover_100.jpg")
        coEvery { bulkUnprocessedImageDao.getById(7L) } returns record

        bulkImageRepository.deleteRecord(7L)

        assertFalse(barcodeFile.exists())
        assertFalse(coverFile.exists())
        coVerify { bulkUnprocessedImageDao.deleteById(7L) }
    }

    @Test
    fun deleteRecord_whenRecordMissing_doesNotDeleteUnrelatedFiles() = runTest {
        val unrelatedFile = writeBulkImage("barcode_999.jpg")
        coEvery { bulkUnprocessedImageDao.getById(7L) } returns null

        bulkImageRepository.deleteRecord(7L)

        assertTrue(unrelatedFile.exists())
        coVerify(exactly = 0) { bulkUnprocessedImageDao.deleteById(any()) }
    }

    @Test
    fun deleteProcessedRecords_removesEveryProcessedPairFromDisk() = runTest {
        val firstRecord = BulkUnprocessedImageEntity(
            id = 1L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = "barcode_1.jpg",
            coverRelFilepath = "cover_1.jpg",
            wasProcessed = true,
        )
        val secondRecord = BulkUnprocessedImageEntity(
            id = 2L,
            createdAtTimestamp = 200L,
            barcodeRelFilepath = "barcode_2.jpg",
            coverRelFilepath = "cover_2.jpg",
            wasProcessed = true,
        )
        val firstBarcodeFile = writeBulkImage("barcode_1.jpg")
        val firstCoverFile = writeBulkImage("cover_1.jpg")
        val secondBarcodeFile = writeBulkImage("barcode_2.jpg")
        val secondCoverFile = writeBulkImage("cover_2.jpg")
        coEvery { bulkUnprocessedImageDao.listProcessedOrderedById() } returns listOf(firstRecord, secondRecord)

        bulkImageRepository.deleteProcessedRecords()

        assertFalse(firstBarcodeFile.exists())
        assertFalse(firstCoverFile.exists())
        assertFalse(secondBarcodeFile.exists())
        assertFalse(secondCoverFile.exists())
        coVerify { bulkUnprocessedImageDao.deleteAllProcessed() }
    }

    private fun writeBulkImage(relativePath: String): File {
        val imageFile = File(filesDirectory, "bulk_scan_work/$relativePath")
        imageFile.parentFile?.mkdirs()
        imageFile.writeText("test-image")
        return imageFile
    }
}
