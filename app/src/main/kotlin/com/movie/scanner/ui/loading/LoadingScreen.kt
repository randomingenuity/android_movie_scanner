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
import com.movie.scanner.util.CsvExporter
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
                            text = { Text("Share list") },
                            onClick = {
                                menuExpanded = false
                                coroutineScope.launch {
                                    val movies = viewModel.loadMoviesForExport()
                                    ShareCsv.shareMoviesCsv(
                                        context = context,
                                        csvContent = CsvExporter.buildCsv(movies),
                                        filename = CsvExporter.buildDefaultFilename(),
                                    )
                                }
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
                Text(text = "Attempt ${uiState.attempt} of 3")
            }
            if (uiState.isError) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        val returnToCamera = uiState.retryAction == LoadingRetryAction.RETAKE_COVER
                        viewModel.retry()
                        if (returnToCamera) {
                            onNavigateBackToCamera()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
