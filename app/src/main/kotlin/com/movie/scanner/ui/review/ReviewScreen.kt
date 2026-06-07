package com.movie.scanner.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.util.BarcodeDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onFinished: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val barcodeFocusRequester = remember { FocusRequester() }
    var barcodeFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var barcodeFieldReady by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.barcode) {
        if (!barcodeFieldReady) {
            val barcode = uiState.barcode
            barcodeFieldValue = TextFieldValue(
                text = barcode,
                selection = TextRange(barcode.length),
            )
            barcodeFieldReady = true
        }
    }

    LaunchedEffect(barcodeFieldReady) {
        if (!barcodeFieldReady) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        barcodeFocusRequester.requestFocus()
        withFrameNanos { }
        withFrameNanos { }
        barcodeFieldValue = barcodeFieldValue.copy(
            selection = TextRange(barcodeFieldValue.text.length),
        )
    }
    BackHandler {
        viewModel.requestDiscard()
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onFinished()
        }
    }

    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardDialog,
            title = { Text("Discard this scan?") },
            text = { Text("Nothing will be added to your list.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDiscard) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscardDialog) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Review") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (uiState.extractedCoverTitle.isNotBlank()) {
                        "Cover title: ${uiState.extractedCoverTitle}"
                    } else {
                        "Cover title: (not detected)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                uiState.barcodeUsageMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = barcodeFieldValue,
                onValueChange = { newValue ->
                    val sanitized = viewModel.updateBarcode(newValue.text)
                    barcodeFieldValue = if (sanitized == newValue.text) {
                        newValue
                    } else {
                        TextFieldValue(
                            text = sanitized,
                            selection = TextRange(
                                start = minOf(newValue.selection.start, sanitized.length),
                                end = minOf(newValue.selection.end, sanitized.length),
                            ),
                        )
                    }
                },
                label = { Text(BarcodeDecoder.buildBarcodeLabel(barcodeFieldValue.text)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(barcodeFocusRequester),
            )
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.year,
                onValueChange = viewModel::updateYear,
                label = { Text("Year") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = uiState.barcodeLlmMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.barcodeSuggestion?.let { suggestion ->
                AssistChip(
                    onClick = viewModel::applyBarcodeSuggestion,
                    label = {
                        Text("Barcode suggests: ${suggestion.title} (${suggestion.year})")
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::searchTmdb) {
                    Text("Re-search TMDB")
                }
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            uiState.searchError?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
            uiState.duplicateMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            uiState.actionMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            Text(
                text = "Confirm movie selection:",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.tmdbResults, key = { it.id }) { result ->
                    TmdbResultRow(
                        result = result,
                        selected = uiState.selectedTmdbResult?.id == result.id,
                        onSelect = { viewModel.selectTmdbResult(result) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::addMovie,
                    enabled = uiState.isAddEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (uiState.showReplaceAdd) "Replace" else "Add")
                }
                if (uiState.showForceAdd) {
                    OutlinedButton(
                        onClick = viewModel::forceAddMovie,
                        enabled = uiState.isForceAddEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Force Add")
                    }
                }
            }
            OutlinedButton(
                onClick = viewModel::skipMovie,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip")
            }
        }
    }
}

@Composable
private fun TmdbResultRow(
    result: TmdbSearchResult,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.posterUrl,
            contentDescription = result.title,
            modifier = Modifier.padding(end = 4.dp),
        )
        Column {
            Text(text = result.title, style = MaterialTheme.typography.bodyLarge)
            Text(text = result.year, style = MaterialTheme.typography.bodyMedium)
            if (selected) {
                Text(text = "Selected", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
