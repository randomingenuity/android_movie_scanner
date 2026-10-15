package com.movie.scanner.util

import android.content.Context
import android.net.Uri
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
}
