package com.movie.scanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.movie.scanner.data.local.BulkUnprocessedImageDao
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.inject.Provider

class BulkImageRepositoryTest {
    private val bulkUnprocessedImageDao = mockk<BulkUnprocessedImageDao>(relaxed = true)
    private val bulkRecognitionProcessor = mockk<BulkRecognitionProcessor>(relaxed = true)
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
        every { bulkRecognitionProcessorProvider.get() } returns bulkRecognitionProcessor
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
    fun saveCapturedPair_withoutBarcode_storesEmptyBarcodePathAndCoverFileOnly() = runTest {
        val coverBitmap = mockk<Bitmap>(relaxed = true)
        every { coverBitmap.isRecycled } returns false
        val insertedRecord = slot<BulkUnprocessedImageEntity>()
        coEvery { bulkUnprocessedImageDao.insert(capture(insertedRecord)) } returns 9L

        bulkImageRepository.saveCapturedPair(barcodeBitmap = null, coverBitmap = coverBitmap)

        assertEquals(
            BulkUnprocessedImageEntity.NO_BARCODE_REL_FILEPATH,
            insertedRecord.captured.barcodeRelFilepath,
        )
        val coverFile = File(filesDirectory, "bulk_scan_work/${insertedRecord.captured.coverRelFilepath}")
        assertTrue(coverFile.isFile)
        val workingDirectory = File(filesDirectory, "bulk_scan_work")
        val barcodeFiles = workingDirectory.listFiles()?.filter { file ->
            file.name.startsWith("barcode_")
        } ?: emptyList()
        assertTrue(barcodeFiles.isEmpty())
    }

    @Test
    fun deleteRecord_withoutBarcodeImage_deletesCoverOnly() = runTest {
        val record = BulkUnprocessedImageEntity(
            id = 8L,
            createdAtTimestamp = 100L,
            barcodeRelFilepath = BulkUnprocessedImageEntity.NO_BARCODE_REL_FILEPATH,
            coverRelFilepath = "cover_100.jpg",
        )
        val coverFile = writeBulkImage("cover_100.jpg")
        coEvery { bulkUnprocessedImageDao.getById(8L) } returns record

        bulkImageRepository.deleteRecord(8L)

        assertFalse(coverFile.exists())
        coVerify { bulkUnprocessedImageDao.deleteById(8L) }
    }

    @Test
    fun replaceCapturedPair_withoutBarcode_replacesCoverAndKeepsBarcodePath() = runTest {
        val existingRecord = BulkUnprocessedImageEntity(
            id = 3L,
            createdAtTimestamp = 50L,
            barcodeRelFilepath = "barcode_50.jpg",
            coverRelFilepath = "cover_50.jpg",
        )
        val barcodeFile = writeBulkImage("barcode_50.jpg")
        val oldCoverFile = writeBulkImage("cover_50.jpg")
        val coverBitmap = mockk<Bitmap>(relaxed = true)
        every { coverBitmap.isRecycled } returns false
        coEvery { bulkUnprocessedImageDao.getById(3L) } returnsMany listOf(
            existingRecord,
            existingRecord.copy(
                coverRelFilepath = "cover_999.jpg",
                createdAtTimestamp = 999L,
            ),
        )

        bulkImageRepository.replaceCapturedPair(
            recordId = 3L,
            barcodeBitmap = null,
            coverBitmap = coverBitmap,
        )

        assertTrue(barcodeFile.exists())
        assertFalse(oldCoverFile.exists())
        val coverPathSlot = slot<String>()
        coVerify {
            bulkUnprocessedImageDao.updateImagePaths(
                recordId = 3L,
                barcodeRelFilepath = "barcode_50.jpg",
                coverRelFilepath = capture(coverPathSlot),
                createdAtTimestamp = any(),
            )
        }
        assertTrue(coverPathSlot.captured.startsWith("cover_"))
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
