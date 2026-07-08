package com.movie.scanner.ui.scanbulk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

/** Fixed width for the status column so the header stays on one line. */
private val BulkQueueStatusColumnWidth = 84.dp
private val BulkQueueRowHorizontalPadding = 12.dp
private val BulkQueueRowVerticalPadding = 4.dp
private val BulkQueueDeleteColumnWidth = 36.dp

private val BulkQueueProcessedCheckColor = Color(0xFF2E7D32)
private val BulkQueuePendingTimerColor = Color(0xFFF9A825)
private val BulkQueueRecognizingDownloadColor = Color(0xFFEF6C00)
private val BulkQueueReadyTimerColor = Color(0xFF2E7D32)

/**
 * Lists bulk-captured image pairs and drives sequential review using pre-fetched recognition data.
 */
@Composable
fun ScanBulkQueueScreen(
    onNavigateToReview: () -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToScan: () -> Unit,
    viewModel: ScanBulkQueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasDoneRecords = uiState.records.any { row -> row.status == BulkQueueItemStatus.PROCESSED }

    BackHandler {
        viewModel.prepareForBulkCapture()
        onNavigateToCapture()
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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(uiState.records, key = { row -> row.id }) { row ->
                    BulkQueueDataRow(
                        row = row,
                        onBarcodeClick = { viewModel.showImagePreview(row.barcodeRelFilepath) },
                        onCoverClick = { viewModel.showImagePreview(row.coverRelFilepath) },
                        onDeleteClick = { viewModel.deleteRecord(row.id) },
                    )
                    HorizontalDivider()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isProcessing) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp,
                        )
                        Text(
                            text = uiState.processingRecordId?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    val hasUnprocessed = uiState.records.any { row ->
                        row.status != BulkQueueItemStatus.PROCESSED
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasUnprocessed) {
                            Button(onClick = viewModel::startProcessing) {
                                Text("Process")
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.prepareForBulkCapture()
                                onNavigateToCapture()
                            },
                        ) {
                            Text("Scan")
                        }
                        if (hasUnprocessed) {
                            Button(
                                onClick = viewModel::exitToScan,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("Exit")
                            }
                        }
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
            modifier = Modifier.width(BulkQueueStatusColumnWidth),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Box(modifier = Modifier.width(BulkQueueDeleteColumnWidth))
    }
}

/**
 * Shows queue status with checkmark, timer, or download icons.
 */
@Composable
private fun BulkQueueStatusIcon(status: BulkQueueItemStatus) {
    Box(
        modifier = Modifier.width(BulkQueueStatusColumnWidth),
        contentAlignment = Alignment.CenterEnd,
    ) {
        when (status) {
            BulkQueueItemStatus.PROCESSED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Processed",
                    tint = BulkQueueProcessedCheckColor,
                )
            }
            BulkQueueItemStatus.RECOGNIZING -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Recognizing",
                    tint = BulkQueueRecognizingDownloadColor,
                )
            }
            BulkQueueItemStatus.READY -> {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Ready for review",
                    tint = BulkQueueReadyTimerColor,
                )
            }
            BulkQueueItemStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Pending recognition",
                    tint = BulkQueuePendingTimerColor,
                )
            }
        }
    }
}

@Composable
private fun BulkQueueDataRow(
    row: ScanBulkQueueRow,
    onBarcodeClick: () -> Unit,
    onCoverClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
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
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = row.timestampLabel,
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Barcode",
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBarcodeClick),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Cover",
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onCoverClick),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        BulkQueueStatusIcon(status = row.status)
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(BulkQueueDeleteColumnWidth),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
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
