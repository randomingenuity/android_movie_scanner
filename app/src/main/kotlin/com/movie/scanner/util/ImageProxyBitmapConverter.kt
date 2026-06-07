package com.movie.scanner.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageProxyBitmapConverter {
    fun convertToBitmap(imageProxy: ImageProxy): Bitmap {
        val decodedBitmap = when (imageProxy.format) {
            ImageFormat.JPEG -> decodeJpeg(imageProxy)
            ImageFormat.YUV_420_888 -> decodeYuv420888(imageProxy)
            else -> throw IllegalArgumentException("Unsupported image format: ${imageProxy.format}")
        }
        return applyRotation(decodedBitmap, imageProxy.imageInfo.rotationDegrees)
    }

    private fun decodeJpeg(imageProxy: ImageProxy): Bitmap {
        if (imageProxy.planes.isEmpty()) {
            throw IllegalStateException("JPEG image has no planes.")
        }
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode JPEG capture.")
    }

    private fun decodeYuv420888(imageProxy: ImageProxy): Bitmap {
        if (imageProxy.planes.size < 3) {
            throw IllegalStateException("YUV image is missing color planes.")
        }
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null,
        )
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, outputStream)
        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("Failed to decode YUV capture.")
    }

    private fun applyRotation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap !== bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }
}
