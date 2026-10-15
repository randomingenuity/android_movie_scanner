package com.movie.scanner.ui.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.repository.CatalogExportPayload
import com.movie.scanner.ui.list.ListExportFormatDialog
import com.movie.scanner.util.ShareCsv
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScreen(
    onNavigateToReview: () -> Unit,
    onNavigateBackToCamera: () -> Unit,
    viewModel: LoadingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }

    if (showExportFormatDialog) {
        ListExportFormatDialog(
            onDismiss = { showExportFormatDialog = false },
            onSelectFormat = { format ->
                showExportFormatDialog = false
                coroutineScope.launch {
                    when (val payload = viewModel.buildCatalogExport(format)) {
                        is CatalogExportPayload.Csv -> {
                            ShareCsv.shareMoviesCsv(
                                context = context,
                                csvContent = payload.content,
                                filename = payload.filename,
                            )
                        }
                        is CatalogExportPayload.Zip -> {
                            ShareCsv.shareCatalogZip(
                                context = context,
                                zipFile = payload.file,
                                filename = payload.filename,
                            )
                        }
                    }
                }
            },
        )
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onNavigateToReview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identifying") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share List") },
                            onClick = {
                                menuExpanded = false
                                showExportFormatDialog = true
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!uiState.isError) {
                CircularProgressIndicator()
            }
            Text(
                text = uiState.message,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (uiState.showAttempt) {
                Text(
                    text = "Attempt ${uiState.attempt}",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (uiState.isError) {
                Text(
                    text = uiState.errorMessage ?: "Something went wrong.",
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = { viewModel.retry() },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Retry")
                }
                Button(
                    onClick = onNavigateBackToCamera,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Retake Cover")
                }
            }
        }
    }
}
