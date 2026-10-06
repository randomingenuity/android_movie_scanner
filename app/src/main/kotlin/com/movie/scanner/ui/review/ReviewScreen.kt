package com.movie.scanner.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.movie.scanner.data.model.DiscType
import com.movie.scanner.data.model.FeatureType
import com.movie.scanner.data.model.MovieEdition
import com.movie.scanner.data.model.TmdbSearchResult
import com.movie.scanner.util.BarcodeDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onFinished: (isBulkProcessing: Boolean) -> Unit,
    onNavigateToBulkRescan: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val bulkReviewSessionKey by viewModel.bulkReviewSessionKey.collectAsStateWithLifecycle()
    val reviewPayloadGeneration by viewModel.reviewPayloadGeneration.collectAsStateWithLifecycle()
    val barcodeFocusRequester = remember { FocusRequester() }
    var barcodeFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var barcodeFieldReady by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf(uiState.title) }
    var yearInput by remember { mutableStateOf(uiState.year) }
    var locationInput by remember { mutableStateOf(uiState.location) }
    var seasonInput by remember { mutableStateOf(uiState.seasonNumberInput) }

    // Sync ViewModel-driven form values in one pass to avoid staggered recompositions.
    LaunchedEffect(
        uiState.title,
        uiState.year,
        uiState.location,
        uiState.seasonNumberInput,
    ) {
        if (titleInput != uiState.title) {
            titleInput = uiState.title
        }
        if (yearInput != uiState.year) {
            yearInput = uiState.year
        }
        if (locationInput != uiState.location) {
            locationInput = uiState.location
        }
        if (seasonInput != uiState.seasonNumberInput) {
            seasonInput = uiState.seasonNumberInput
        }
    }
    LaunchedEffect(bulkReviewSessionKey) {
        if (bulkReviewSessionKey == 0) {
            return@LaunchedEffect
        }
        titleInput = uiState.title
        yearInput = uiState.year
        locationInput = uiState.location
        seasonInput = uiState.seasonNumberInput
        val barcode = uiState.barcode
        barcodeFieldValue = if (barcode.isEmpty()) {
            TextFieldValue(text = "", selection = TextRange(0))
        } else {
            TextFieldValue(text = barcode)
        }
        barcodeFieldReady = true
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

    LaunchedEffect(reviewPayloadGeneration) {
        viewModel.consumeReviewPayloadFromSessionIfNeeded()
    }
    LaunchedEffect(viewModel) {
        viewModel.navigationEventFlow.collect { event ->
            if (event is ReviewNavigationEvent.NavigateToBulkRescan) {
                onNavigateToBulkRescan()
            }
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
            TopAppBar(
                title = { Text("Review") },
                actions = {
                    if (uiState.isBulkProcessing) {
                        Button(
                            onClick = viewModel::stopBulkProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Stop Processing")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val barcodeLabel = remember(barcodeFieldValue.text) {
            BarcodeDecoder.buildBarcodeLabel(barcodeFieldValue.text)
        }
        val formFields = remember(
            titleInput,
            yearInput,
            barcodeFieldValue.text,
            locationInput,
            seasonInput,
            uiState.numberOfDiscsInput,
        ) {
            ReviewFormFields(
                title = titleInput,
                year = yearInput,
                barcode = barcodeFieldValue.text,
                location = locationInput,
                seasonNumberInput = seasonInput,
                numberOfDiscsInput = uiState.numberOfDiscsInput,
            )
        }
        val titleYearChangedFromTmdbSearch = remember(
            titleInput,
            yearInput,
            uiState.tmdbSyncedTitle,
            uiState.tmdbSyncedYear,
        ) {
            titleInput.trim() != uiState.tmdbSyncedTitle.trim() ||
                yearInput.trim() != uiState.tmdbSyncedYear.trim()
        }
        val uriHandler = LocalUriHandler.current
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
                        OutlinedButton(onClick = viewModel::requestBulkRescan) {
                            Text("Rescan")
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
            item(key = "cover_summary_${uiState.selectedTmdbResult?.id}_${uiState.tmdbResults.firstOrNull()?.id}") {
                ReviewCoverSummarySection(
                    uiState = uiState,
                    actionState = actionState,
                    title = titleInput,
                    year = yearInput,
                    barcode = barcodeFieldValue.text,
                )
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
            item(key = "tmdb_refresh") {
                ReviewTmdbRefreshButton(
                    formFields = formFields,
                    titleYearChangedFromTmdbSearch = titleYearChangedFromTmdbSearch,
                    requiresManualTitleEntry = uiState.requiresManualTitleEntry,
                    actionState = actionState,
                    onRefresh = { viewModel.searchTmdb(formFields) },
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
                item(key = "tmdb_results_label") {
                    Text(
                        text = "Confirm movie selection:",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item(key = "tmdb_results_header") {
                    TmdbResultTableHeaderRow()
                }
                item(key = "tmdb_results_header_divider") {
                    HorizontalDivider()
                }
                items(
                    items = uiState.tmdbResults,
                    key = { result -> result.id },
                ) { result ->
                    TmdbResultTableRow(
                        result = result,
                        selected = uiState.selectedTmdbResult?.id == result.id,
                        onSelect = { viewModel.selectTmdbResult(result) },
                        onOpenTmdb = { uriHandler.openUri(result.tmdbUrl) },
                    )
                    HorizontalDivider()
                }
            }
            item(key = "disc_type_field") {
                ReviewDiscTypeField(
                    selectedDiscType = uiState.discType,
                    onDiscTypeSelected = viewModel::updateDiscType,
                )
            }
            if (uiState.featureType == FeatureType.MOVIE) {
                item(key = "edition_field") {
                    ReviewEditionField(
                        selectedEdition = uiState.edition,
                        onEditionSelected = viewModel::updateEdition,
                    )
                }
            }
            item(key = "number_of_discs_field") {
                ReviewNumberOfDiscsField(
                    numberOfDiscs = uiState.numberOfDiscsInput,
                    onNumberOfDiscsChange = viewModel::updateNumberOfDiscsInput,
                )
            }
            item(key = "action_buttons") {
                ReviewActionButtons(
                    actionState = actionState,
                    onGoBack = viewModel::goBackToLastAddedMovie,
                    onAdd = { viewModel.addMovie(formFields) },
                    onSkip = viewModel::skipMovie,
                    onForceAdd = { viewModel.forceAddMovie(formFields) },
                )
            }
            item(key = "search_error") {
                ReviewSearchErrorMessage(searchError = actionState.searchError)
            }
            item(key = "duplicate_message") {
                ReviewDuplicateMessage(duplicateMessage = actionState.duplicateMessage)
            }
            item(key = "action_message") {
                ReviewActionMessage(actionMessage = actionState.actionMessage)
            }
            item(key = "location_field") {
                OutlinedTextField(
                    value = locationInput,
                    onValueChange = {
                        locationInput = it
                        viewModel.scheduleLocationUpdate(it)
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
 * Cover OCR summary plus parameter source and TMDB match lines under the feature-type picker.
 */
@Composable
private fun ReviewCoverSummarySection(
    uiState: ReviewUiState,
    actionState: ReviewActionState,
    title: String,
    year: String,
    barcode: String,
) {
    val uriHandler = LocalUriHandler.current
    val parametersFromComment = ReviewViewModel.buildParametersFromComment(
        title = title,
        year = year,
        barcode = barcode,
        recognizedTitle = uiState.recognizedTitle,
        recognizedYear = uiState.recognizedYear,
        recognizedBarcode = uiState.recognizedBarcode,
        automaticParameterSource = uiState.automaticParameterSource,
    )
    val matchedResult = uiState.selectedTmdbResult ?: uiState.tmdbResults.firstOrNull()
    val hasTitleAndYear = title.trim().isNotEmpty() && year.trim().isNotEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (uiState.extractedCoverTitle.isNotBlank()) {
                "Cover title: ${uiState.extractedCoverTitle}"
            } else if (uiState.automaticParameterSource == ReviewAutomaticParameterSource.BARCODE_LOOKUP) {
                "Cover title: (not used)"
            } else {
                "Cover title: (not detected)"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Parameters from: $parametersFromComment",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.requiresManualTitleEntry) {
            Text(
                text = ReviewViewModel.MANUAL_TITLE_ENTRY_WARNING,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Matched: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                matchedResult != null -> {
                    Text(
                        text = matchedResult.id.toString(),
                        modifier = Modifier.clickable {
                            uriHandler.openUri(matchedResult.tmdbUrl)
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                }
                actionState.isSearching || hasTitleAndYear -> {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        text = "(none)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Re-runs TMDB search when Title or Year changed, or when retrying after a search error.
 */
@Composable
private fun ReviewTmdbRefreshButton(
    formFields: ReviewFormFields,
    titleYearChangedFromTmdbSearch: Boolean,
    requiresManualTitleEntry: Boolean,
    actionState: ReviewActionState,
    onRefresh: () -> Unit,
) {
    val canRefreshTmdb = formFields.title.trim().isNotEmpty() &&
        formFields.year.trim().isNotEmpty() &&
        !actionState.isSearching &&
        (
            titleYearChangedFromTmdbSearch ||
                actionState.searchError != null ||
                requiresManualTitleEntry
            )
    OutlinedButton(
        onClick = onRefresh,
        enabled = canRefreshTmdb,
    ) {
        if (actionState.isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            Text("Refresh")
        }
    }
}

/**
 * Primary review actions laid out in fixed rows to avoid FlowRow measurement cost while scrolling.
 */
@Composable
private fun ReviewActionButtons(
    actionState: ReviewActionState,
    onGoBack: () -> Unit,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
    onForceAdd: () -> Unit,
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
            OutlinedButton(
                onClick = onGoBack,
                enabled = actionState.isBackEnabled,
            ) {
                Text("Back")
            }
            Button(
                onClick = onAdd,
                enabled = actionState.isAddEnabled,
            ) {
                Text(if (actionState.showReplaceAdd) "Replace" else "Add")
            }
            OutlinedButton(onClick = onSkip) {
                Text("Skip")
            }
        }
        if (actionState.showForceAdd) {
            OutlinedButton(
                onClick = onForceAdd,
                enabled = actionState.isForceAddEnabled,
            ) {
                Text(if (actionState.showForceReplace) "Force Replace" else "Force Add")
            }
        }
    }
}

@Composable
private fun ReviewSearchErrorMessage(searchError: String?) {
    searchError?.let { error ->
        Text(text = error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ReviewDuplicateMessage(duplicateMessage: String?) {
    duplicateMessage?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReviewActionMessage(actionMessage: String?) {
    actionMessage?.let { message ->
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * Bounded stepper for disc count on the review form (1–10, default 1).
 */
@Composable
private fun ReviewNumberOfDiscsField(
    numberOfDiscs: Int,
    onNumberOfDiscsChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Number of Discs",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onNumberOfDiscsChange(numberOfDiscs - 1) },
                enabled = numberOfDiscs > ReviewViewModel.MIN_NUMBER_OF_DISCS,
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease number of discs",
                )
            }
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = numberOfDiscs.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(
                onClick = { onNumberOfDiscsChange(numberOfDiscs + 1) },
                enabled = numberOfDiscs < ReviewViewModel.MAX_NUMBER_OF_DISCS,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase number of discs",
                )
            }
        }
    }
}

/**
 * Main feature disc type picker using a dialog; OutlinedButton avoids a scroll-blocking overlay.
 */
@Composable
private fun ReviewDiscTypeField(
    selectedDiscType: String?,
    onDiscTypeSelected: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val discTypeOptions = remember { DiscType.options }
    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Main Feature Disc Type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (selectedDiscType != null) {
                        DiscType.labelForStored(selectedDiscType)
                    } else {
                        "Required"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Main Feature Disc Type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    discTypeOptions.forEach { discType ->
                        TextButton(
                            onClick = {
                                onDiscTypeSelected(discType.value)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = discType.label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
        )
    }
}

/**
 * Edition picker for movies using a dialog; OutlinedButton avoids a scroll-blocking overlay.
 */
@Composable
private fun ReviewEditionField(
    selectedEdition: String?,
    onEditionSelected: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val editionOptions = remember { MovieEdition.options }
    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Edition",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (selectedEdition != null) {
                        MovieEdition.labelForStored(selectedEdition)
                    } else {
                        "Required"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Edition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    editionOptions.forEach { edition ->
                        TextButton(
                            onClick = {
                                onEditionSelected(edition.value)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = edition.label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
        )
    }
}

private val TmdbResultTableRowHeight = 36.dp

/**
 * Column headers for the TMDB result pick table (Name, Year, Open).
 */
@Composable
private fun TmdbResultTableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TmdbResultTableRowHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Name",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Year",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.width(40.dp))
    }
}

/**
 * One selectable TMDB result row with an Open action that launches the TMDB page in the browser.
 */
@Composable
private fun TmdbResultTableRow(
    result: TmdbSearchResult,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenTmdb: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TmdbResultTableRowHeight)
            .background(backgroundColor)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.year,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(48.dp),
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onOpenTmdb,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
