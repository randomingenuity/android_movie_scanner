package com.movie.scanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.movie.scanner.data.local.BulkUnprocessedImageDao
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BulkImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bulkUnprocessedImageDao: BulkUnprocessedImageDao,
) {
    private val workingDirectoryName = "bulk_scan_work"

    fun observeAllRecords(): Flow<List<BulkUnprocessedImageEntity>> =
        bulkUnprocessedImageDao.observeAllOrderedById()

    suspend fun listAllRecords(): List<BulkUnprocessedImageEntity> =
        bulkUnprocessedImageDao.listAllOrderedById()

    suspend fun listUnprocessedRecords(): List<BulkUnprocessedImageEntity> =
        bulkUnprocessedImageDao.listUnprocessedOrderedById()

    suspend fun markProcessed(recordId: Long) {
        bulkUnprocessedImageDao.markProcessed(recordId)
    }

    /**
     * Removes a bulk queue row and deletes its barcode/cover image files from disk.
     */
    suspend fun deleteRecord(recordId: Long) = withContext(Dispatchers.IO) {
        val record = bulkUnprocessedImageDao.getById(recordId) ?: return@withContext
        val workingDirectory = resolveWorkingDirectory()
        File(workingDirectory, record.barcodeRelFilepath).delete()
        File(workingDirectory, record.coverRelFilepath).delete()
        bulkUnprocessedImageDao.deleteById(recordId)
    }

    /**
     * Persists a barcode/cover pair under the app working directory and indexes the row.
     */
    suspend fun saveCapturedPair(
        barcodeBitmap: Bitmap,
        coverBitmap: Bitmap,
    ): Long = withContext(Dispatchers.IO) {
        val workingDirectory = resolveWorkingDirectory()
        val captureTimestamp = System.currentTimeMillis()
        val barcodeRelativePath = "barcode_$captureTimestamp.jpg"
        val coverRelativePath = "cover_$captureTimestamp.jpg"
        writeBitmap(
            destinationFile = File(workingDirectory, barcodeRelativePath),
            bitmap = barcodeBitmap,
        )
        writeBitmap(
            destinationFile = File(workingDirectory, coverRelativePath),
            bitmap = coverBitmap,
        )
        if (!barcodeBitmap.isRecycled) {
            barcodeBitmap.recycle()
        }
        if (!coverBitmap.isRecycled) {
            coverBitmap.recycle()
        }
        bulkUnprocessedImageDao.insert(
            BulkUnprocessedImageEntity(
                createdAtTimestamp = captureTimestamp,
                barcodeRelFilepath = barcodeRelativePath,
                coverRelFilepath = coverRelativePath,
            ),
        )
    }

    suspend fun loadBitmap(relativeFilepath: String): Bitmap? = withContext(Dispatchers.IO) {
        val imageFile = File(resolveWorkingDirectory(), relativeFilepath)
        if (!imageFile.isFile) {
            return@withContext null
        }
        android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
    }

    fun resolveAbsolutePath(relativeFilepath: String): String =
        File(resolveWorkingDirectory(), relativeFilepath).absolutePath

    private fun resolveWorkingDirectory(): File =
        File(context.filesDir, workingDirectoryName).also { directory ->
            if (!directory.exists()) {
                directory.mkdirs()
            }
        }

    private fun writeBitmap(destinationFile: File, bitmap: Bitmap) {
        FileOutputStream(destinationFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        }
    }
}
