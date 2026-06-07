package com.movie.scanner.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

object ZxingBarcodeReader {
    private val supportedFormats = listOf(
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
    )

    private val decodeHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to supportedFormats,
        DecodeHintType.TRY_HARDER to true,
    )

    fun decodeBarcode(bitmap: Bitmap): String? {
        return decodeBarcodeWithBinarizer(bitmap, useHybridBinarizer = true)
            ?: decodeBarcodeWithBinarizer(bitmap, useHybridBinarizer = false)
    }

    private fun decodeBarcodeWithBinarizer(
        bitmap: Bitmap,
        useHybridBinarizer: Boolean,
    ): String? {
        val reader = MultiFormatReader()
        reader.setHints(decodeHints)
        val binaryBitmap = buildBinaryBitmap(bitmap, useHybridBinarizer) ?: return null
        return try {
            val result = reader.decode(binaryBitmap)
            normalizeBarcodeDigits(result.text)
        } catch (exception: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun buildBinaryBitmap(
        bitmap: Bitmap,
        useHybridBinarizer: Boolean,
    ): BinaryBitmap? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminanceSource = RGBLuminanceSource(width, height, pixels)
        return if (useHybridBinarizer) {
            BinaryBitmap(HybridBinarizer(luminanceSource))
        } else {
            BinaryBitmap(GlobalHistogramBinarizer(luminanceSource))
        }
    }

    private fun normalizeBarcodeDigits(value: String): String? {
        val digitsOnly = value.filter { character -> character.isDigit() }
        if (digitsOnly.isNotEmpty()) {
            return digitsOnly
        }
        val trimmed = value.trim()
        return trimmed.ifBlank { null }
    }
}
