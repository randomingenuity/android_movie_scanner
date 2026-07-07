package com.movie.scanner.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.movie.scanner.data.model.DiscType
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.util.BarcodeDecoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    onFinished: (isBulkProcessing: Boolean) -> Unit,
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
            onFinished(uiState.finishedFromBulkProcessing)
        }
    }

    if (uiState.showBulkCoverPreview && uiState.bulkCoverAbsolutePath != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBulkCoverPreview,
            confirmButton = {
                TextButton(onClick = viewModel::dismissBulkCoverPreview) {
                    Text("Close")
                }
            },
            title = { Text("Cover preview") },
            text = {
                AsyncImage(
                    model = uiState.bulkCoverAbsolutePath,
                    contentDescription = "Bulk scan cover preview",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            },
        )
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
        val barcodeLabel = remember(barcodeFieldValue.text) {
            BarcodeDecoder.buildBarcodeLabel(barcodeFieldValue.text)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isBulkProcessing) {
                item(key = "bulk_processing_actions") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.bulkCoverAbsolutePath != null) {
                            OutlinedButton(onClick = viewModel::showBulkCoverPreview) {
                                Text("Show Cover")
                            }
                        }
                        Button(
                            onClick = viewModel::stopBulkProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Stop Processing")
                        }
                    }
                }
            }
            item(key = "feature_type") {
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
            }
            item(key = "cover_summary") {
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
            }
            item(key = "title_field") {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (uiState.featureType == FeatureType.TV) {
                item(key = "season_field") {
                    OutlinedTextField(
                        value = uiState.seasonNumberInput,
                        onValueChange = viewModel::updateSeasonNumberInput,
                        label = { Text("Season") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            item(key = "year_barcode_row") {
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
                        label = { Text(barcodeLabel) },
                        modifier = Modifier
                            .weight(0.65f)
                            .focusRequester(barcodeFocusRequester),
                    )
                }
            }
            item(key = "barcode_llm_message") {
                Text(
                    text = uiState.barcodeLlmMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val barcodeSuggestion = uiState.barcodeSuggestion
            if (barcodeSuggestion != null) {
                item(key = "barcode_suggestion") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = viewModel::applyBarcodeSuggestion,
                            label = { Text("Alt title") },
                        )
                        Text(
                            text = "${barcodeSuggestion.title} (${barcodeSuggestion.year})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (uiState.tmdbResults.size > 1) {
                item(key = "tmdb_results_header") {
                    Text(
                        text = "Confirm movie selection:",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(
                    items = uiState.tmdbResults,
                    key = { result -> result.id },
                ) { result ->
                    TmdbResultRow(
                        result = result,
                        selected = uiState.selectedTmdbResult?.id == result.id,
                        onSelect = { viewModel.selectTmdbResult(result) },
                    )
                }
            }
            item(key = "disc_type_field") {
                ReviewDiscTypeField(
                    selectedDiscType = uiState.discType,
                    onDiscTypeSelected = viewModel::updateDiscType,
                )
            }
            item(key = "number_of_discs_field") {
                OutlinedTextField(
                    value = uiState.numberOfDiscsInput,
                    onValueChange = viewModel::updateNumberOfDiscsInput,
                    label = { Text("Number of Discs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item(key = "action_buttons") {
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
            }
            uiState.searchError?.let { error ->
                item(key = "search_error") {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.duplicateMessage?.let { message ->
                item(key = "duplicate_message") {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            uiState.actionMessage?.let { message ->
                item(key = "action_message") {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
            item(key = "location_field") {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDiscTypeField(
    selectedDiscType: String?,
    onDiscTypeSelected: (String?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val discTypeOptions = remember { listOf(null) + DiscType.options }
    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { menuExpanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedDiscType.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Disc Type") },
            placeholder = { Text("Optional") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
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
private fun TmdbResultRow(
    result: TmdbSearchResult,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val posterRequest = remember(result.posterUrl) {
        ImageRequest.Builder(context)
            .data(result.posterUrl)
            .size(Size(96, 144))
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = posterRequest,
            contentDescription = result.title,
            modifier = Modifier
                .size(48.dp)
                .padding(end = 4.dp),
            contentScale = ContentScale.Crop,
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
