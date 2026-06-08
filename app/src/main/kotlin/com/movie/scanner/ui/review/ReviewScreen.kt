package com.movie.scanner.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.movie.scanner.data.model.DiscType
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.util.BarcodeDecoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        val barcode = uiState.barcode
        if (!barcodeFieldReady) {
            barcodeFieldValue = if (barcode.isEmpty()) {
                TextFieldValue(text = "", selection = TextRange(0))
            } else {
                TextFieldValue(text = barcode)
            }
            barcodeFieldReady = true
        } else if (barcodeFieldValue.text != barcode) {
            barcodeFieldValue = TextFieldValue(text = barcode)
        }
    }

    LaunchedEffect(barcodeFieldReady) {
        if (!barcodeFieldReady || barcodeFieldValue.text.isNotEmpty()) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        barcodeFocusRequester.requestFocus()
        withFrameNanos { }
        withFrameNanos { }
        barcodeFieldValue = barcodeFieldValue.copy(selection = TextRange(0))
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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FeatureType.entries.forEachIndexed { index, featureType ->
                    SegmentedButton(
                        selected = uiState.featureType == featureType,
                        onClick = { viewModel.updateFeatureType(featureType) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FeatureType.entries.size,
                        ),
                    ) {
                        Text(featureType.label)
                    }
                }
            }
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
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.featureType == FeatureType.TV) {
                OutlinedTextField(
                    value = uiState.seasonNumberInput,
                    onValueChange = viewModel::updateSeasonNumberInput,
                    label = { Text("Season") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.year,
                    onValueChange = viewModel::updateYear,
                    label = { Text("Year") },
                    modifier = Modifier.weight(0.35f),
                )
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
                        .weight(0.65f)
                        .focusRequester(barcodeFocusRequester),
                )
            }
            Text(
                text = uiState.barcodeLlmMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.barcodeSuggestion?.let { suggestion ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = viewModel::applyBarcodeSuggestion,
                        label = { Text("Alt title") },
                    )
                    Text(
                        text = "${suggestion.title} (${suggestion.year})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (uiState.tmdbResults.size > 1) {
                Text(
                    text = "Confirm movie selection:",
                    style = MaterialTheme.typography.titleMedium,
                )
                ReviewTmdbResultsList(
                    results = uiState.tmdbResults,
                    selectedTmdbResultId = uiState.selectedTmdbResult?.id,
                    onSelectResult = viewModel::selectTmdbResult,
                )
            }
            ReviewDiscTypeField(
                selectedDiscType = uiState.discType,
                onDiscTypeSelected = viewModel::updateDiscType,
            )
            OutlinedTextField(
                value = uiState.numberOfDiscsInput,
                onValueChange = viewModel::updateNumberOfDiscsInput,
                label = { Text("Number of Discs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = viewModel::searchTmdb) {
                    Text("Re-search TMDB")
                }
                Button(
                    onClick = viewModel::addMovie,
                    enabled = uiState.isAddEnabled,
                ) {
                    Text(if (uiState.showReplaceAdd) "Replace" else "Add")
                }
                OutlinedButton(onClick = viewModel::skipMovie) {
                    Text("Skip")
                }
                if (uiState.showForceAdd) {
                    OutlinedButton(
                        onClick = viewModel::forceAddMovie,
                        enabled = uiState.isForceAddEnabled,
                    ) {
                        Text(if (uiState.showForceReplace) "Force Replace" else "Force Add")
                    }
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
            OutlinedTextField(
                value = uiState.location,
                onValueChange = viewModel::updateLocation,
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ReviewDiscTypeField(
    selectedDiscType: String?,
    onDiscTypeSelected: (String?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val discTypeOptions = remember { listOf(null) + DiscType.options }
    val openMenu = { menuExpanded = true }
    val menuInteractionSource = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedDiscType.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Disc Type") },
            placeholder = { Text("Optional") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null,
                    onClick = openMenu,
                ),
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            discTypeOptions.forEach { discType ->
                DropdownMenuItem(
                    text = { Text(discType ?: "None") },
                    onClick = {
                        onDiscTypeSelected(discType)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ReviewTmdbResultsList(
    results: List<TmdbSearchResult>,
    selectedTmdbResultId: Int?,
    onSelectResult: (TmdbSearchResult) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        results.forEach { result ->
            TmdbResultRow(
                result = result,
                selected = selectedTmdbResultId == result.id,
                onSelect = { onSelectResult(result) },
            )
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
            modifier = Modifier
                .size(48.dp)
                .padding(end = 4.dp),
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
