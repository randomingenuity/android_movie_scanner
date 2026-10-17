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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.model.DiscType
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.ui.camera.CameraPreview
import com.movie.scanner.ui.camera.CaptureProgressOverlay
import com.movie.scanner.ui.camera.ShutterButton
import com.movie.scanner.ui.navigation.ScanBulkNavigationViewModel

/**
 * Streamlined bulk capture flow: alternate barcode and cover photos per movie.
 */
@Composable
fun ScanBulkCaptureScreen(
    scanBulkNavigationViewModel: ScanBulkNavigationViewModel,
    onNavigateToQueue: () -> Unit,
    onNavigateToLoading: () -> Unit,
    onNavigateBackToReview: () -> Unit,
    viewModel: ScanBulkCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bulkDefaultsPromptUiState by scanBulkNavigationViewModel.bulkDefaultsPromptUiState
        .collectAsStateWithLifecycle()
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

    BackHandler(enabled = bulkDefaultsPromptUiState.showBulkDefaultsPrompt) {
        scanBulkNavigationViewModel.dismissBulkDefaultsPrompt()
    }
    BackHandler(enabled = bulkDefaultsPromptUiState.showDiscTypeDialog && !bulkDefaultsPromptUiState.showBulkDefaultsPrompt) {
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

    LifecycleResumeEffect(viewModel, uiState.isRescanMode) {
        scanBulkNavigationViewModel.offerBulkDefaultsPromptIfNeeded(skipForRescan = uiState.isRescanMode)
        viewModel.onCaptureScreenResumed()
        viewModel.refreshBatchHeaderDefaults()
        onPauseOrDispose { }
    }

    LaunchedEffect(
        bulkDefaultsPromptUiState.showBulkDefaultsPrompt,
        bulkDefaultsPromptUiState.showDiscTypeDialog,
        bulkDefaultsPromptUiState.showLocationDialog,
    ) {
        viewModel.refreshBatchHeaderDefaults()
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
    var showCameraPreview by remember { mutableStateOf(false) }
    var headerChromeHeightPx by remember { mutableIntStateOf(0) }
    var footerChromeHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(hasCameraPermission, uiState.isConfigured) {
        if (hasCameraPermission && uiState.isConfigured) {
            withFrameMillis { }
            showCameraPreview = true
        } else {
            showCameraPreview = false
        }
    }

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

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showCameraPreview) {
                CameraPreview(
                    onImageCaptured = viewModel::processCapturedImage,
                    onCaptureFailed = viewModel::onCaptureFailed,
                    onRegisterCapture = { captureAction.value = it },
                    modifier = Modifier.fillMaxSize(),
                    previewCropTopInsetPx = headerChromeHeightPx,
                    previewCropBottomInsetPx = footerChromeHeightPx,
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size -> headerChromeHeightPx = size.height },
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
                        if (!uiState.isRescanMode) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val bulkBatchDiscTypeLabel = DiscType.labelForStored(uiState.bulkBatchDiscType)
                                if (bulkBatchDiscTypeLabel.isBlank()) {
                                    Button(onClick = scanBulkNavigationViewModel::openDiscTypeDialog) {
                                        Text("Main Feature Disc Type")
                                    }
                                } else {
                                    Text(
                                        text = bulkBatchDiscTypeLabel,
                                        modifier = Modifier.clickable(onClick = scanBulkNavigationViewModel::openDiscTypeDialog),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    )
                                }
                                if (uiState.bulkBatchLocation.isNotBlank()) {
                                    Text(
                                        text = uiState.bulkBatchLocation,
                                        modifier = Modifier.clickable(onClick = scanBulkNavigationViewModel::openLocationDialog),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    )
                                } else {
                                    Button(onClick = scanBulkNavigationViewModel::openLocationDialog) {
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
                Spacer(modifier = Modifier.weight(1f))
                if (!uiState.isCapturing && !uiState.isProcessingCapture) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > 0) {
                                    footerChromeHeightPx = size.height
                                }
                            },
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (uiState.captureMode == ScanCaptureMode.BARCODE) {
                                    Button(onClick = viewModel::skipBarcode) {
                                        Text("Skip")
                                    }
                                }
                            }
                            ShutterButton(
                                onClick = {
                                    viewModel.beginCaptureProcessing()
                                    captureAction.value?.invoke()
                                },
                            )
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            CaptureProgressOverlay(
                isCapturing = uiState.isCapturing,
                isProcessingCapture = uiState.isProcessingCapture,
            )
        }
    }
}
