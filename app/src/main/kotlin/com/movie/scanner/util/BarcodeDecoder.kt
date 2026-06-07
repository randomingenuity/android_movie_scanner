package com.movie.scanner.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object BarcodeDecoder {
    private const val DECODE_TIMEOUT_SECONDS = 5L
    private const val MIN_CROP_DECODE_WIDTH = 500
    private const val CROP_PADDING_FRACTION = 0.20f
    private const val MAX_DECODE_DIMENSION = 2048
    private val mlKitScannerLock = Any()

    fun buildBarcodeScanner(): BarcodeScanner {
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                )
                .build(),
        )
    }

    fun decodeFromImageProxy(
        imageProxy: ImageProxy,
        barcodeScanner: BarcodeScanner,
    ): String? {
        val bitmap = ImageProxyBitmapConverter.convertToBitmap(imageProxy)
        return decodeFromBitmap(bitmap, barcodeScanner)
    }

    fun decodeFromBitmap(
        bitmap: Bitmap,
        barcodeScanner: BarcodeScanner,
    ): String? {
        val workingBitmap = downscaleBitmapForDecoding(bitmap, MAX_DECODE_DIMENSION)
        try {
            for (rotationDegrees in listOf(0, 90, 180, 270)) {
                val orientedBitmap = rotateBitmap(workingBitmap, rotationDegrees)
                val detected = decodeOrientedBitmap(orientedBitmap, barcodeScanner)
                if (detected != null) {
                    if (orientedBitmap !== workingBitmap) {
                        orientedBitmap.recycle()
                    }
                    return detected
                }
                if (orientedBitmap !== workingBitmap) {
                    orientedBitmap.recycle()
                }
            }
            return null
        } finally {
            if (workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }
        }
    }

    fun buildBarcodeLabel(value: String): String {
        val digitsOnly = value.filter { character -> character.isDigit() }
        return when {
            digitsOnly.length == 13 && digitsOnly.startsWith("978") -> "ISBN"
            digitsOnly.length == 13 && digitsOnly.startsWith("979") -> "ISBN"
            digitsOnly.length == 13 && digitsOnly.startsWith("977") -> "ISSN"
            else -> "Barcode"
        }
    }

    fun formatBarcodeLine(value: String): String {
        val label = buildBarcodeLabel(value)
        val digitsOnly = value.filter { character -> character.isDigit() }
        val displayValue = digitsOnly.ifBlank { value }
        return "$label: $displayValue"
    }

    private fun decodeOrientedBitmap(
        bitmap: Bitmap,
        barcodeScanner: BarcodeScanner,
    ): String? {
        val locatedBarcodes = detectBarcodesWithMlKit(bitmap, barcodeScanner)
        extractBarcodeValue(selectBestBarcode(locatedBarcodes))?.let { return it }

        for (locatedBarcode in locatedBarcodes) {
            val crop = cropAroundBoundingBox(bitmap, locatedBarcode.boundingBox) ?: continue
            val detected = decodeCroppedBitmap(crop, barcodeScanner)
            if (crop !== bitmap) {
                crop.recycle()
            }
            if (detected != null) {
                return detected
            }
        }

        for (searchRegion in buildSearchRegions(bitmap.width, bitmap.height)) {
            val crop = cropRect(bitmap, searchRegion) ?: continue
            val detected = decodeCroppedBitmap(crop, barcodeScanner)
            if (crop !== bitmap) {
                crop.recycle()
            }
            if (detected != null) {
                return detected
            }
        }

        return decodeCroppedBitmap(bitmap, barcodeScanner)
    }

    private fun decodeCroppedBitmap(
        crop: Bitmap,
        barcodeScanner: BarcodeScanner,
    ): String? {
        val scaledCrop = upscaleBitmapIfNeeded(crop, MIN_CROP_DECODE_WIDTH)
        try {
            ZxingBarcodeReader.decodeBarcode(scaledCrop)?.let { return it }

            val locatedBarcodes = detectBarcodesWithMlKit(scaledCrop, barcodeScanner)
            extractBarcodeValue(selectBestBarcode(locatedBarcodes))?.let { return it }

            if (scaledCrop.width < MIN_CROP_DECODE_WIDTH * 2) {
                val enlargedCrop = Bitmap.createScaledBitmap(
                    scaledCrop,
                    scaledCrop.width * 2,
                    scaledCrop.height * 2,
                    true,
                )
                try {
                    ZxingBarcodeReader.decodeBarcode(enlargedCrop)?.let { return it }
                    val enlargedBarcodes = detectBarcodesWithMlKit(enlargedCrop, barcodeScanner)
                    extractBarcodeValue(selectBestBarcode(enlargedBarcodes))?.let { return it }
                } finally {
                    if (enlargedCrop !== scaledCrop) {
                        enlargedCrop.recycle()
                    }
                }
            }
        } finally {
            if (scaledCrop !== crop) {
                scaledCrop.recycle()
            }
        }
        return null
    }

    private fun detectBarcodesWithMlKit(
        bitmap: Bitmap,
        barcodeScanner: BarcodeScanner,
    ): List<Barcode> {
        var locatedBarcodes = emptyList<Barcode>()
        val latch = CountDownLatch(1)
        synchronized(mlKitScannerLock) {
            barcodeScanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    locatedBarcodes = barcodes
                    latch.countDown()
                }
                .addOnFailureListener {
                    latch.countDown()
                }
        }
        latch.await(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return locatedBarcodes
    }

    private fun downscaleBitmapForDecoding(
        bitmap: Bitmap,
        maxDimension: Int,
    ): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) {
            return bitmap
        }
        val scale = maxDimension.toFloat() / largestSide.toFloat()
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    private fun cropAroundBoundingBox(
        bitmap: Bitmap,
        boundingBox: Rect?,
    ): Bitmap? {
        if (boundingBox == null) {
            return null
        }
        val paddingHorizontal = (boundingBox.width() * CROP_PADDING_FRACTION).toInt()
        val paddingVertical = (boundingBox.height() * CROP_PADDING_FRACTION).toInt()
        val cropRect = Rect(
            (boundingBox.left - paddingHorizontal).coerceAtLeast(0),
            (boundingBox.top - paddingVertical).coerceAtLeast(0),
            (boundingBox.right + paddingHorizontal).coerceAtMost(bitmap.width),
            (boundingBox.bottom + paddingVertical).coerceAtMost(bitmap.height),
        )
        return cropRect(bitmap, cropRect)
    }

    private fun cropRect(
        bitmap: Bitmap,
        cropRect: Rect,
    ): Bitmap? {
        val width = cropRect.width()
        val height = cropRect.height()
        if (width <= 0 || height <= 0) {
            return null
        }
        return Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            width,
            height,
        )
    }

    private fun buildSearchRegions(
        imageWidth: Int,
        imageHeight: Int,
    ): List<Rect> {
        val horizontalMargin = (imageWidth * 0.10f).toInt()
        val verticalMargin = (imageHeight * 0.10f).toInt()
        return listOf(
            Rect(0, imageHeight / 2, imageWidth, imageHeight),
            Rect(0, (imageHeight * 2) / 3, imageWidth, imageHeight),
            Rect(
                horizontalMargin,
                verticalMargin,
                imageWidth - horizontalMargin,
                imageHeight - verticalMargin,
            ),
            Rect(0, 0, imageWidth, imageHeight / 2),
        )
    }

    private fun upscaleBitmapIfNeeded(
        bitmap: Bitmap,
        minimumWidth: Int,
    ): Bitmap {
        if (bitmap.width >= minimumWidth) {
            return bitmap
        }
        val scale = minimumWidth.toFloat() / bitmap.width.toFloat()
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, minimumWidth, scaledHeight, true)
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int,
    ): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun selectBestBarcode(barcodes: List<Barcode>): Barcode? {
        if (barcodes.isEmpty()) {
            return null
        }
        return barcodes.minByOrNull { barcode -> barcodePriority(barcode) }
    }

    private fun barcodePriority(barcode: Barcode): Int {
        return when {
            barcode.valueType == Barcode.TYPE_ISBN -> 0
            barcode.format == Barcode.FORMAT_EAN_13 -> 1
            barcode.format == Barcode.FORMAT_UPC_A -> 2
            barcode.format == Barcode.FORMAT_UPC_E -> 3
            barcode.format == Barcode.FORMAT_EAN_8 -> 4
            barcode.format == Barcode.FORMAT_CODE_128 -> 5
            barcode.format == Barcode.FORMAT_CODE_39 -> 6
            else -> 99
        }
    }

    private fun extractBarcodeValue(barcode: Barcode?): String? {
        if (barcode == null) {
            return null
        }
        val candidate = barcode.rawValue?.takeIf { value -> value.isNotBlank() }
            ?: barcode.displayValue?.takeIf { value -> value.isNotBlank() }
            ?: return null
        val digitsOnly = candidate.filter { character -> character.isDigit() }
        if (digitsOnly.isNotEmpty()) {
            return digitsOnly
        }
        return candidate.trim()
    }
}
