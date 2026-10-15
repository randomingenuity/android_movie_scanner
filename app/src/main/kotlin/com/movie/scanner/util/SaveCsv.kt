package com.movie.scanner.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

object SaveCsv {
    /**
     * Writes catalog CSV bytes to a URI returned by the system save (CreateDocument) flow.
     */
    fun writeMoviesCsv(context: Context, destinationUri: Uri, csvContent: String) {
        val outputStream =
            context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Could not open output stream for save destination")
        outputStream.use { stream ->
            stream.write(csvContent.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    fun writeFileToUri(context: Context, destinationUri: Uri, sourceFile: File) {
        val outputStream =
            context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Could not open output stream for save destination")
        sourceFile.inputStream().use { inputStream ->
            outputStream.use { stream ->
                inputStream.copyTo(stream)
                stream.flush()
            }
        }
    }
}
