package com.movie.scanner.ui.camera

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.movie.scanner.util.ImageProxyBitmapConverter
import java.util.concurrent.Executors

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
