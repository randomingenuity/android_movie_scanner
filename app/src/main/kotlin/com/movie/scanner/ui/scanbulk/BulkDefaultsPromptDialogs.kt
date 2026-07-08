package com.movie.scanner.ui.scanbulk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movie.scanner.data.model.DiscType
import com.movie.scanner.ui.navigation.BulkDefaultsPromptUiState

/**
 * Bulk session defaults prompt and the disc type / location setup dialogs shared by queue and capture.
 */
@Composable
fun BulkDefaultsPromptDialogs(
    uiState: BulkDefaultsPromptUiState,
    onDismissBulkDefaultsPrompt: () -> Unit,
    onAcceptBulkDefaultsSetup: () -> Unit,
    onDismissDiscTypeDialog: () -> Unit,
    onSaveBulkDiscType: (String?) -> Unit,
    onDismissLocationDialog: () -> Unit,
    onSaveBulkLocation: (String) -> Unit,
) {
    val discTypeOptions = remember { listOf(null) + DiscType.options }
    var locationDraft by remember { mutableStateOf("") }

    LaunchedEffect(uiState.showLocationDialog) {
        if (uiState.showLocationDialog) {
            locationDraft = uiState.bulkLocation
        }
    }

    if (uiState.showBulkDefaultsPrompt) {
        AlertDialog(
            onDismissRequest = onDismissBulkDefaultsPrompt,
            text = {
                Text("Your defaults are not set for this session. Set now?")
            },
            confirmButton = {
                Button(
                    onClick = onAcceptBulkDefaultsSetup,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(
                    onClick = onDismissBulkDefaultsPrompt,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("No")
                }
            },
        )
    }

    if (uiState.showDiscTypeDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscTypeDialog,
            title = { Text("Disc Type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    discTypeOptions.forEach { discType ->
                        TextButton(
                            onClick = { onSaveBulkDiscType(discType) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = discType ?: "None",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissDiscTypeDialog) {
                    Text("Cancel")
                }
            },
        )
    }

    if (uiState.showLocationDialog) {
        AlertDialog(
            onDismissRequest = onDismissLocationDialog,
            title = { Text("Set Location") },
            text = {
                OutlinedTextField(
                    value = locationDraft,
                    onValueChange = { locationDraft = it },
                    label = { Text("Location name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onSaveBulkLocation(locationDraft) },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLocationDialog) {
                    Text("Cancel")
                }
            },
        )
    }
}
