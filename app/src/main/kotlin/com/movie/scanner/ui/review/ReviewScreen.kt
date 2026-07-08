package com.movie.scanner.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onFinished: (isBulkProcessing: Boolean) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val barcodeFocusRequester = remember { FocusRequester() }
    var barcodeFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var barcodeFieldReady by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf(uiState.title) }
    var yearInput by remember { mutableStateOf(uiState.year) }
    var locationInput by remember { mutableStateOf(uiState.location) }
    var seasonInput by remember { mutableStateOf(uiState.seasonNumberInput) }
    var discsInput by remember { mutableStateOf(uiState.numberOfDiscsInput) }
    LaunchedEffect(uiState.title) {
        if (titleInput != uiState.title) {
            titleInput = uiState.title
        }
    }
    LaunchedEffect(uiState.year) {
        if (yearInput != uiState.year) {
            yearInput = uiState.year
        }
    }
    LaunchedEffect(uiState.location) {
        if (locationInput != uiState.location) {
            locationInput = uiState.location
        }
    }
    LaunchedEffect(uiState.seasonNumberInput) {
        if (seasonInput != uiState.seasonNumberInput) {
            seasonInput = uiState.seasonNumberInput
        }
    }
    LaunchedEffect(uiState.numberOfDiscsInput) {
        if (discsInput != uiState.numberOfDiscsInput) {
            discsInput = uiState.numberOfDiscsInput
        }
    }
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
        val formFields = ReviewFormFields(
            title = titleInput,
            year = yearInput,
            barcode = barcodeFieldValue.text,
            location = locationInput,
            seasonNumberInput = seasonInput,
            numberOfDiscsInput = discsInput,
        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    value = titleInput,
                    onValueChange = {
                        titleInput = it
                        viewModel.scheduleTitleUpdate(it)
                    },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (uiState.featureType == FeatureType.TV) {
                item(key = "season_field") {
                    OutlinedTextField(
                        value = seasonInput,
                        onValueChange = {
                            seasonInput = it
                            viewModel.scheduleSeasonNumberUpdate(it)
                        },
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
                        value = yearInput,
                        onValueChange = {
                            yearInput = it
                            viewModel.scheduleYearUpdate(it)
                        },
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
                            viewModel.scheduleBarcodeUpdate(sanitized)
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
                    value = discsInput,
                    onValueChange = { discsInput = it },
                    label = { Text("Number of Discs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item(key = "action_buttons") {
                ReviewActionButtons(
                    formFields = formFields,
                    isAddEnabled = uiState.isAddEnabled,
                    showReplaceAdd = uiState.showReplaceAdd,
                    showForceAdd = uiState.showForceAdd,
                    showForceReplace = uiState.showForceReplace,
                    isForceAddEnabled = uiState.isForceAddEnabled,
                    isSearching = uiState.isSearching,
                    onSearchTmdb = viewModel::searchTmdb,
                    onAddMovie = viewModel::addMovie,
                    onSkipMovie = viewModel::skipMovie,
                    onForceAddMovie = viewModel::forceAddMovie,
                )
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
                    value = locationInput,
                    onValueChange = {
                        locationInput = it
                        viewModel.updateLocation(it)
                    },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

/**
 * Primary review actions laid out in fixed rows to avoid FlowRow measurement cost while scrolling.
 */
@Composable
private fun ReviewActionButtons(
    formFields: ReviewFormFields,
    isAddEnabled: Boolean,
    showReplaceAdd: Boolean,
    showForceAdd: Boolean,
    showForceReplace: Boolean,
    isForceAddEnabled: Boolean,
    isSearching: Boolean,
    onSearchTmdb: (ReviewFormFields) -> Unit,
    onAddMovie: (ReviewFormFields) -> Unit,
    onSkipMovie: () -> Unit,
    onForceAddMovie: (ReviewFormFields) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { onSearchTmdb(formFields) }) {
                Text("Re-search TMDB")
            }
            Button(
                onClick = { onAddMovie(formFields) },
                enabled = isAddEnabled,
            ) {
                Text(if (showReplaceAdd) "Replace" else "Add")
            }
            OutlinedButton(onClick = onSkipMovie) {
                Text("Skip")
            }
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        if (showForceAdd) {
            OutlinedButton(
                onClick = { onForceAddMovie(formFields) },
                enabled = isForceAddEnabled,
            ) {
                Text(if (showForceReplace) "Force Replace" else "Force Add")
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
