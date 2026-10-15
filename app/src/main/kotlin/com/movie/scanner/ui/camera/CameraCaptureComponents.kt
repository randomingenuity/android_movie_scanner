package com.movie.scanner.ui.camera

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.movie.scanner.util.BitmapFillCenterCrop
import com.movie.scanner.util.ImageProxyBitmapConverter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val CAPTURE_TARGET_WIDTH = 1920
private const val CAPTURE_TARGET_HEIGHT = 1080
private const val CAPTURE_JPEG_QUALITY = 90

/**
 * Full-screen camera preview with a registered still-capture callback.
 */
@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onRegisterCapture: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCapture = remember {
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(CAPTURE_TARGET_WIDTH, CAPTURE_TARGET_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
            .setJpegQuality(CAPTURE_JPEG_QUALITY)
            .build()
    }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val cameraProviderReference = remember { AtomicReference<ProcessCameraProvider?>(null) }
    var previewLayoutWidth by remember { mutableStateOf(0) }
    var previewLayoutHeight by remember { mutableStateOf(0) }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProviderReference.set(cameraProvider)
            val preview = Preview.Builder().build().also { useCase ->
                useCase.surfaceProvider = previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }, mainExecutor)
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProviderReference.get()?.unbindAll()
            cameraProviderReference.set(null)
            captureExecutor.shutdownNow()
        }
    }

    LaunchedEffect(imageCapture, previewLayoutWidth, previewLayoutHeight) {
        val layoutWidth = previewLayoutWidth
        val layoutHeight = previewLayoutHeight
        onRegisterCapture {
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val decodedBitmap = ImageProxyBitmapConverter.convertToBitmap(image)
                            val bitmap = BitmapFillCenterCrop.cropToViewAspectRatio(
                                decodedBitmap,
                                layoutWidth,
                                layoutHeight,
                            )
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
        update = { view ->
            if (view.width > 0 && view.height > 0) {
                if (previewLayoutWidth != view.width || previewLayoutHeight != view.height) {
                    previewLayoutWidth = view.width
                    previewLayoutHeight = view.height
                }
            }
        },
    )
}

/**
 * Full-screen overlay shown while the camera is capturing or the app is processing a photo.
 */
@Composable
fun CaptureProgressOverlay(
    isCapturing: Boolean,
    isProcessingCapture: Boolean,
) {
    val statusMessage = when {
        isCapturing -> "Capturing"
        isProcessingCapture -> "Processing photo…"
        else -> return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = statusMessage,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Circular shutter control used on scan capture screens.
 */
@Composable
fun ShutterButton(
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
