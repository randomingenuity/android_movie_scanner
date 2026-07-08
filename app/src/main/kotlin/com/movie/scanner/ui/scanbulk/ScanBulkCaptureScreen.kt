package com.movie.scanner.ui.scanbulk

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.ui.camera.CameraPreview
import com.movie.scanner.ui.camera.CaptureProgressOverlay
import com.movie.scanner.ui.camera.ShutterButton

/**
 * Streamlined bulk capture flow: alternate barcode and cover photos per movie.
 */
@Composable
fun ScanBulkCaptureScreen(
    onNavigateToQueue: () -> Unit,
    onNavigateToLoading: () -> Unit,
    onNavigateBackToReview: () -> Unit,
    viewModel: ScanBulkCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    BackHandler(enabled = uiState.showLocationDialog) {
        viewModel.dismissLocationDialog()
    }
    BackHandler(enabled = !uiState.showLocationDialog) {
        if (uiState.isRescanMode) {
            viewModel.cancelRescan()
        } else {
            viewModel.finishScanning()
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        viewModel.prepareScreen()
        onDispose {
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.onCaptureScreenResumed()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEventFlow.collect { event ->
            when (event) {
                ScanBulkCaptureEvent.NavigateToQueue -> onNavigateToQueue()
                ScanBulkCaptureEvent.NavigateToLoading -> onNavigateToLoading()
                ScanBulkCaptureEvent.NavigateToReview -> onNavigateBackToReview()
            }
        }
    }

    if (!uiState.isConfigured) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Configure TMDB and at least one LLM API key in Settings before scanning.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Camera permission is required to scan movies.")
            Button(
                onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Grant camera permission")
            }
        }
        return
    }

    val captureAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    var locationDraft by remember { mutableStateOf("") }
    val headerLabel = if (uiState.isRescanMode) {
        if (uiState.captureMode == ScanCaptureMode.BARCODE) {
            "Rescan Barcode"
        } else {
            "Rescan Cover"
        }
    } else if (uiState.captureMode == ScanCaptureMode.BARCODE) {
        "Barcode ${uiState.currentPairNumber}"
    } else {
        "Cover ${uiState.currentPairNumber}"
    }

    LaunchedEffect(uiState.showLocationDialog) {
        if (uiState.showLocationDialog) {
            locationDraft = uiState.bulkLocation
        }
    }

    if (uiState.showLocationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLocationDialog,
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
                    onClick = { viewModel.saveBulkLocation(locationDraft) },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLocationDialog) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            CameraPreview(
                onImageCaptured = viewModel::processCapturedImage,
                onCaptureFailed = viewModel::onCaptureFailed,
                onRegisterCapture = { captureAction.value = it },
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerLabel,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = uiState.statusMessage,
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        uiState.captureErrorMessage?.let { errorMessage ->
                            Text(
                                text = errorMessage,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = viewModel::clearCaptureError,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!uiState.isRescanMode) {
                            if (uiState.bulkLocation.isNotBlank()) {
                                Text(
                                    text = uiState.bulkLocation,
                                    modifier = Modifier.clickable(onClick = viewModel::openLocationDialog),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                )
                            } else {
                                TextButton(onClick = viewModel::openLocationDialog) {
                                    Text("Location")
                                }
                            }
                            Button(
                                onClick = viewModel::finishScanning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
            if (!uiState.isCapturing && !uiState.isProcessingCapture) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ShutterButton(
                        onClick = {
                            viewModel.beginCaptureProcessing()
                            captureAction.value?.invoke()
                        },
                    )
                }
            }
            CaptureProgressOverlay(
                isCapturing = uiState.isCapturing,
                isProcessingCapture = uiState.isProcessingCapture,
            )
        }
    }
}
