package com.movie.scanner.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareCsv {
    fun shareMoviesCsv(context: Context, csvContent: String, filename: String) {
        val cacheDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val csvFile = File(cacheDirectory, filename)
        csvFile.writeText(csvContent, Charsets.UTF_8)
        shareFile(
            context = context,
            file = csvFile,
            mimeType = "text/csv",
            chooserTitle = "Share movie list",
            clipLabel = filename,
        )
    }

    fun shareCatalogZip(context: Context, zipFile: File, filename: String) {
        shareFile(
            context = context,
            file = zipFile,
            mimeType = "application/zip",
            chooserTitle = "Share movie list",
            clipLabel = filename,
        )
    }

    private fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String,
        clipLabel: String,
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, clipLabel, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooserIntent)
    }
}
