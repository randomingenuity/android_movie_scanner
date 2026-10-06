package com.movie.scanner.ui.scanbulk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.movie.scanner.R
import com.movie.scanner.ui.navigation.ScanBulkNavigationViewModel

/** Trailing action icons: optional barcode, status, and delete. */
private val BulkQueueIconSize = 24.dp
private val BulkQueueIconSpacing = 8.dp
private val BulkQueueTrailingWidth =
    BulkQueueIconSize * 3 + BulkQueueIconSpacing * 2
private val BulkQueueRowHorizontalPadding = 12.dp
private val BulkQueueRowVerticalPadding = 4.dp

private val BulkQueueProcessedCheckColor = Color(0xFF2E7D32)
private val BulkQueuePendingTimerColor = Color(0xFFF9A825)
private val BulkQueueRecognizingDownloadColor = Color(0xFFEF6C00)
private val BulkQueueReadyTimerColor = Color(0xFF2E7D32)
private val BulkQueueBarcodeResultColor = Color(0xFF1565C0)
private val BulkQueueBarcodeRescanColor = Color(0xFF6A1B9A)
private const val BulkQueueAlreadyInListRowAlpha = 0.45f

/**
 * Lists bulk-captured image pairs and drives sequential review using pre-fetched recognition data.
 */
@Composable
fun ScanBulkQueueScreen(
    scanBulkNavigationViewModel: ScanBulkNavigationViewModel,
    onNavigateToReview: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToScan: () -> Unit,
    viewModel: ScanBulkQueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bulkDefaultsPromptUiState by scanBulkNavigationViewModel.bulkDefaultsPromptUiState
        .collectAsStateWithLifecycle()
    val records = uiState.records
    val hasDoneRecords = remember(records) {
        records.any { row -> row.status == BulkQueueItemStatus.PROCESSED }
    }
    val hasUnprocessedRecords = remember(records) {
        records.any { row -> row.status != BulkQueueItemStatus.PROCESSED }
    }
    val showInitialLoading = uiState.isLoadingRecords && records.isEmpty()
    var showClearQueueDialog by remember { mutableStateOf(false) }

    if (showClearQueueDialog) {
        AlertDialog(
            onDismissRequest = { showClearQueueDialog = false },
            title = { Text("Clear queue?") },
            text = {
                Text(
                    "Remove all ${records.size} items from the bulk queue? " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllRecords()
                        showClearQueueDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearQueueDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    BackHandler(enabled = bulkDefaultsPromptUiState.showBulkDefaultsPrompt) {
        scanBulkNavigationViewModel.dismissBulkDefaultsPrompt()
    }
    BackHandler(
        enabled = bulkDefaultsPromptUiState.showDiscTypeDialog && !bulkDefaultsPromptUiState.showBulkDefaultsPrompt,
    ) {
        scanBulkNavigationViewModel.dismissDiscTypeDialog()
    }
    BackHandler(
        enabled = bulkDefaultsPromptUiState.showLocationDialog &&
            !bulkDefaultsPromptUiState.showDiscTypeDialog &&
            !bulkDefaultsPromptUiState.showBulkDefaultsPrompt,
    ) {
        scanBulkNavigationViewModel.dismissLocationDialog()
    }
    BackHandler(
        enabled = !bulkDefaultsPromptUiState.showBulkDefaultsPrompt &&
            !bulkDefaultsPromptUiState.showLocationDialog &&
            !bulkDefaultsPromptUiState.showDiscTypeDialog,
    ) {
        viewModel.prepareForBulkCapture()
        onNavigateToCapture()
    }

    LifecycleResumeEffect(Unit) {
        scanBulkNavigationViewModel.offerBulkDefaultsPromptIfNeeded()
        onPauseOrDispose { }
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.resumeProcessingIfNeeded()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEventFlow.collect { event ->
            when (event) {
                ScanBulkQueueEvent.NavigateToReview -> onNavigateToReview()
                ScanBulkQueueEvent.NavigateToScan -> onNavigateToScan()
                ScanBulkQueueEvent.NavigateToCapture -> onNavigateToCapture()
            }
        }
    }

    uiState.previewImagePath?.let { relativeFilepath ->
        BulkImagePreviewDialog(
            absolutePath = viewModel.resolveAbsolutePath(relativeFilepath),
            onDismiss = viewModel::dismissImagePreview,
        )
    }

    Scaffold { innerPadding ->
        if (showInitialLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (hasDoneRecords && !uiState.isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = BulkQueueRowHorizontalPadding,
                            vertical = 8.dp,
                        ),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = viewModel::clearDoneRecords) {
                        Text("Clear Done")
                    }
                }
            }
            BulkQueueHeaderRow()
            HorizontalDivider()
            BulkQueueRecordsList(
                records = records,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onBarcodeClick = viewModel::showImagePreview,
                onCoverClick = viewModel::showImagePreview,
                onRescanClick = viewModel::requestRescan,
                onDeleteClick = viewModel::deleteRecord,
            )
            BulkQueueFooter(
                isProcessing = uiState.isProcessing,
                processingRecordId = uiState.processingRecordId,
                hasUnprocessedRecords = hasUnprocessedRecords,
                showExit = hasUnprocessedRecords || records.isEmpty(),
                hasRecords = records.isNotEmpty(),
                onStartProcessing = viewModel::startProcessing,
                onScan = {
                    viewModel.prepareForBulkCapture()
                    onNavigateToCapture()
                },
                onExit = viewModel::exitToScan,
                onClear = { showClearQueueDialog = true },
            )
        }
    }
}

/**
 * Scrollable bulk queue rows; isolated so processing footer updates do not recompose the list.
 */
@Composable
private fun BulkQueueRecordsList(
    records: List<ScanBulkQueueRow>,
    modifier: Modifier = Modifier,
    onBarcodeClick: (String) -> Unit,
    onCoverClick: (String) -> Unit,
    onRescanClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(
            items = records,
            key = { row -> row.id },
            contentType = { _ -> "bulk_queue_row" },
        ) { row ->
            BulkQueueDataRow(
                row = row,
                onBarcodeClick = { onBarcodeClick(row.barcodeRelFilepath) },
                onCoverClick = { onCoverClick(row.coverRelFilepath) },
                onRescanClick = { onRescanClick(row.id) },
                onDeleteClick = { onDeleteClick(row.id) },
            )
            HorizontalDivider()
        }
    }
}

/**
 * Process / Scan / Exit controls and the in-flight processing indicator.
 */
@Composable
private fun BulkQueueFooter(
    isProcessing: Boolean,
    processingRecordId: Long?,
    hasUnprocessedRecords: Boolean,
    showExit: Boolean,
    hasRecords: Boolean,
    onStartProcessing: () -> Unit,
    onScan: () -> Unit,
    onExit: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isProcessing) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp,
                )
                Text(
                    text = processingRecordId?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasUnprocessedRecords) {
                    Button(onClick = onStartProcessing) {
                        Text("Process")
                    }
                }
                Button(onClick = onScan) {
                    Text("Scan")
                }
                if (showExit) {
                    Button(
                        onClick = onExit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Exit")
                    }
                }
                if (hasRecords) {
                    Button(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkQueueHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BulkQueueRowHorizontalPadding,
                vertical = BulkQueueRowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ID",
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Timestamp",
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Barcode",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Cover",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Status",
            modifier = Modifier.width(BulkQueueTrailingWidth),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * Shows optional barcode or rescan, status, and delete icons with equal spacing between neighbors.
 */
@Composable
private fun BulkQueueTrailingIcons(
    status: BulkQueueItemStatus,
    showBarcodeResultIcon: Boolean,
    showBarcodeRescanIcon: Boolean,
    isBarcodeAlreadyInList: Boolean,
    onRescanClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val dimLeadingIcons = if (isBarcodeAlreadyInList) {
        Modifier.alpha(BulkQueueAlreadyInListRowAlpha)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier.width(BulkQueueTrailingWidth),
        horizontalArrangement = Arrangement.spacedBy(BulkQueueIconSpacing, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = dimLeadingIcons,
            horizontalArrangement = Arrangement.spacedBy(BulkQueueIconSpacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBarcodeResultIcon) {
                Icon(
                    painter = painterResource(R.drawable.barcode),
                    contentDescription = "Identified via barcode",
                    modifier = Modifier.size(BulkQueueIconSize),
                    tint = BulkQueueBarcodeResultColor,
                )
            } else if (showBarcodeRescanIcon && !isBarcodeAlreadyInList) {
                Icon(
                    painter = painterResource(R.drawable.restart_alt),
                    contentDescription = "Rescan barcode and cover",
                    modifier = Modifier
                        .size(BulkQueueIconSize)
                        .clickable(onClick = onRescanClick),
                    tint = BulkQueueBarcodeRescanColor,
                )
            }
            BulkQueueStatusIcon(status = status)
        }
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            modifier = Modifier
                .size(BulkQueueIconSize)
                .clickable(onClick = onDeleteClick),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Shows the queue status as a checkmark, timer, or download icon.
 */
@Composable
private fun BulkQueueStatusIcon(status: BulkQueueItemStatus) {
    when (status) {
        BulkQueueItemStatus.PROCESSED -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Processed",
                modifier = Modifier.size(BulkQueueIconSize),
                tint = BulkQueueProcessedCheckColor,
            )
        }
        BulkQueueItemStatus.RECOGNIZING -> {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Recognizing",
                modifier = Modifier.size(BulkQueueIconSize),
                tint = BulkQueueRecognizingDownloadColor,
            )
        }
        BulkQueueItemStatus.READY -> {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Ready for review",
                modifier = Modifier.size(BulkQueueIconSize),
                tint = BulkQueueReadyTimerColor,
            )
        }
        BulkQueueItemStatus.PENDING -> {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Pending recognition",
                modifier = Modifier.size(BulkQueueIconSize),
                tint = BulkQueuePendingTimerColor,
            )
        }
    }
}

@Composable
private fun BulkQueueDataRow(
    row: ScanBulkQueueRow,
    onBarcodeClick: () -> Unit,
    onCoverClick: () -> Unit,
    onRescanClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val dimRowContent = if (row.isBarcodeAlreadyInList) {
        Modifier.alpha(BulkQueueAlreadyInListRowAlpha)
    } else {
        Modifier
    }
    val linkColor = if (row.isBarcodeAlreadyInList) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BulkQueueRowHorizontalPadding,
                vertical = BulkQueueRowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.id.toString(),
            modifier = Modifier
                .weight(0.7f)
                .then(dimRowContent),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = row.timestampLabel,
            modifier = Modifier
                .weight(1.5f)
                .then(dimRowContent),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Barcode",
            modifier = Modifier
                .weight(1f)
                .then(dimRowContent)
                .then(
                    if (row.isBarcodeAlreadyInList) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onBarcodeClick)
                    },
                ),
            color = linkColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Cover",
            modifier = Modifier
                .weight(1f)
                .then(dimRowContent)
                .then(
                    if (row.isBarcodeAlreadyInList) {
                        Modifier
                    } else {
                        Modifier.clickable(onClick = onCoverClick)
                    },
                ),
            color = linkColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        BulkQueueTrailingIcons(
            status = row.status,
            showBarcodeResultIcon = row.showBarcodeResultIcon,
            showBarcodeRescanIcon = row.showBarcodeRescanIcon,
            isBarcodeAlreadyInList = row.isBarcodeAlreadyInList,
            onRescanClick = onRescanClick,
            onDeleteClick = onDeleteClick,
        )
    }
}

@Composable
private fun BulkImagePreviewDialog(
    absolutePath: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Image preview") },
        text = {
            AsyncImage(
                model = absolutePath,
                contentDescription = "Bulk scan image preview",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        },
    )
}
