package com.movie.scanner.ui.scan

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.util.ImageProxyBitmapConverter
import java.util.concurrent.Executors

@Composable
fun ScanScreen(
    onNavigateToLoading: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
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

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        viewModel.prepareScreen()
        onDispose {
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(Unit) {
        viewModel.consumeAddedMessage()?.let { title ->
            snackbarHostState.showSnackbar("Added $title")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.captureProcessingEventFlow.collect { event ->
            if (event is CaptureProcessingEvent.NavigateToLoading) {
                onNavigateToLoading()
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
    var manualEntryTitle by remember { mutableStateOf("") }
    var manualEntryYear by remember { mutableStateOf("") }

    if (uiState.showManualEntryDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissManualEntry,
            title = { Text("Manual Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualEntryTitle,
                        onValueChange = { manualEntryTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = manualEntryYear,
                        onValueChange = { manualEntryYear = it },
                        label = { Text("Year") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    uiState.manualEntryError?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.submitManualEntry(manualEntryTitle, manualEntryYear)) {
                            manualEntryTitle = ""
                            manualEntryYear = ""
                            onNavigateToLoading()
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissManualEntry) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
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
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = if (uiState.captureMode == ScanCaptureMode.BARCODE) "Barcode" else "Cover",
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
            }
            if (!uiState.isProcessingCapture) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
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
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (uiState.captureMode == ScanCaptureMode.COVER) {
                            Button(onClick = viewModel::showManualEntry) {
                                Text("Manual Entry")
                            }
                        }
                    }
                }
            }
            if (uiState.isProcessingCapture) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Processing photo…",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(width = 4.dp, color = Color.White, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(color = Color.White, shape = CircleShape),
        )
    }
}

@Composable
private fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onRegisterCapture: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            captureExecutor.shutdownNow()
        }
    }

    LaunchedEffect(imageCapture) {
        onRegisterCapture {
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val bitmap = ImageProxyBitmapConverter.convertToBitmap(image)
                            mainExecutor.execute {
                                onImageCaptured(bitmap)
                            }
                        } catch (exception: Exception) {
                            mainExecutor.execute {
                                onCaptureFailed(
                                    exception.message ?: "Could not decode the captured image.",
                                )
                            }
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute {
                            onCaptureFailed(exception.message ?: "Camera capture failed.")
                        }
                    }
                },
            )
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

