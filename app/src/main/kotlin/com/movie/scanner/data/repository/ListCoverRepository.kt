package com.movie.scanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.movie.scanner.data.model.MovieEntity
import com.movie.scanner.util.ListCoverFilename
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListCoverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val catalogDirectoryName = "list_catalog_covers"

    fun resolveAbsolutePath(coverRelFilepath: String): File =
        File(resolveCatalogDirectory(), coverRelFilepath)

    fun coverFileExists(coverRelFilepath: String?): Boolean {
        if (coverRelFilepath.isNullOrBlank()) {
            return false
        }
        return resolveAbsolutePath(coverRelFilepath).isFile
    }

    /**
     * Copies or writes a captured cover into catalog storage and returns its relative path.
     */
    suspend fun persistCoverForMovie(
        movie: MovieEntity,
        sourceCoverFile: File?,
        coverBitmap: Bitmap?,
        manualCaptureBasename: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (sourceCoverFile == null && coverBitmap == null) {
            return@withContext null
        }
        val manualBasename = manualCaptureBasename
            ?: sourceCoverFile?.name
            ?: "cover_${System.currentTimeMillis()}.jpg"
        val targetRelFilepath = ListCoverFilename.buildTargetRelFilepath(movie, manualBasename)
            ?: return@withContext null
        val destinationFile = resolveAbsolutePath(targetRelFilepath)
        destinationFile.parentFile?.mkdirs()
        val previousRelFilepath = movie.coverRelFilepath
        if (!previousRelFilepath.isNullOrBlank() && previousRelFilepath != targetRelFilepath) {
            deleteCoverFile(previousRelFilepath)
        }
        when {
            sourceCoverFile != null && sourceCoverFile.isFile -> {
                sourceCoverFile.inputStream().use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            coverBitmap != null -> {
                FileOutputStream(destinationFile).use { outputStream ->
                    coverBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
            }
            else -> return@withContext null
        }
        targetRelFilepath
    }

    suspend fun deleteCoverFile(coverRelFilepath: String) = withContext(Dispatchers.IO) {
        val coverFile = resolveAbsolutePath(coverRelFilepath)
        if (coverFile.isFile) {
            coverFile.delete()
        }
    }

    suspend fun clearAllCovers() = withContext(Dispatchers.IO) {
        val catalogDirectory = resolveCatalogDirectory()
        if (catalogDirectory.isDirectory) {
            catalogDirectory.deleteRecursively()
        }
    }

    private fun resolveCatalogDirectory(): File =
        File(context.filesDir, catalogDirectoryName).also { directory ->
            if (!directory.isDirectory) {
                directory.mkdirs()
            }
        }
}
