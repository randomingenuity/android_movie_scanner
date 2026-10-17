package com.movie.scanner.ui.camera

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.movie.scanner.util.BitmapFillCenterCrop
import com.movie.scanner.util.ImageProxyBitmapConverter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val CAPTURE_TARGET_WIDTH = 1920
private const val CAPTURE_TARGET_HEIGHT = 1080
private const val CAPTURE_JPEG_QUALITY = 90

/**
 * Latest width and height of the [PreviewView] in pixels, read when a still is captured.
 */
private data class PreviewLayoutSize(
    val width: Int,
    val height: Int,
)

/** Chrome inset heights in preview pixels, read when a still is captured. */
private data class PreviewChromeCropInsets(
    val topPx: Int,
    val bottomPx: Int,
)

/**
 * Full-screen camera preview with a registered still-capture callback.
 *
 * @param previewCropTopInsetPx Pixels of [PreviewView] height covered by top chrome (for still crop).
 * @param previewCropBottomInsetPx Pixels of [PreviewView] height covered by bottom chrome (for still crop).
 */
@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onRegisterCapture: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    previewCropTopInsetPx: Int = 0,
    previewCropBottomInsetPx: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(AndroidColor.BLACK)
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
    val boundPreviewReference = remember { AtomicReference<Preview?>(null) }
    val bindGeneration = remember { AtomicInteger(0) }
    val previewLayoutSizeReference = remember { AtomicReference(PreviewLayoutSize(0, 0)) }
    val previewChromeCropInsetsReference = remember {
        AtomicReference(PreviewChromeCropInsets(0, 0))
    }
    previewChromeCropInsetsReference.set(
        PreviewChromeCropInsets(
            topPx = previewCropTopInsetPx,
            bottomPx = previewCropBottomInsetPx,
        ),
    )

    fun unbindCameraUseCases() {
        val cameraProvider = cameraProviderReference.get()
        val boundPreview = boundPreviewReference.getAndSet(null)
        if (cameraProvider != null) {
            boundPreview?.let { previewUseCase ->
                cameraProvider.unbind(previewUseCase)
            }
            cameraProvider.unbind(imageCapture)
        }
    }

    fun bindCameraUseCases(generation: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            if (generation != bindGeneration.get()) {
                return@addListener
            }
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return@addListener
            }
            previewView.post {
                if (generation != bindGeneration.get()) {
                    return@post
                }
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    return@post
                }
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderReference.set(cameraProvider)
                val preview = Preview.Builder().build().also { useCase ->
                    useCase.surfaceProvider = previewView.surfaceProvider
                }
                boundPreviewReference.set(preview)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }
        }, mainExecutor)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val generation = bindGeneration.incrementAndGet()
                    bindCameraUseCases(generation)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    bindGeneration.incrementAndGet()
                    unbindCameraUseCases()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val generation = bindGeneration.incrementAndGet()
            bindCameraUseCases(generation)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bindGeneration.incrementAndGet()
            unbindCameraUseCases()
            cameraProviderReference.set(null)
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
                            val decodedBitmap = ImageProxyBitmapConverter.convertToBitmap(image)
                            val layoutSize = previewLayoutSizeReference.get()
                            val chromeCropInsets = previewChromeCropInsetsReference.get()
                            val cropTopInset = chromeCropInsets.topPx
                            val cropBottomInset = chromeCropInsets.bottomPx
                            val bitmap = if (
                                cropTopInset > 0 ||
                                cropBottomInset > 0
                            ) {
                                BitmapFillCenterCrop.cropToVisiblePreview(
                                    decodedBitmap,
                                    layoutSize.width,
                                    layoutSize.height,
                                    cropTopInset,
                                    cropBottomInset,
                                )
                            } else {
                                BitmapFillCenterCrop.cropToViewAspectRatio(
                                    decodedBitmap,
                                    layoutSize.width,
                                    layoutSize.height,
                                )
                            }
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
        modifier = modifier
            .clipToBounds()
            .fillMaxSize(),
        update = { view ->
            if (view.width > 0 && view.height > 0) {
                previewLayoutSizeReference.set(PreviewLayoutSize(view.width, view.height))
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
