package com.movie.scanner.ui.list

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.movie.scanner.util.CatalogExportFormat

/**
 * Asks whether to export CSV only or a ZIP that includes cover images.
 */
@Composable
fun ListExportFormatDialog(
    onDismiss: () -> Unit,
    onSelectFormat: (CatalogExportFormat) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export List") },
        text = {
            Text(
                "Export the catalog as CSV only, or as a ZIP with the CSV and captured cover images.",
            )
        },
        confirmButton = {
            TextButton(onClick = { onSelectFormat(CatalogExportFormat.ZipWithCovers) }) {
                Text("ZIP With Covers")
            }
        },
        dismissButton = {
            TextButton(onClick = { onSelectFormat(CatalogExportFormat.CsvOnly) }) {
                Text("CSV Only")
            }
        },
    )
}
