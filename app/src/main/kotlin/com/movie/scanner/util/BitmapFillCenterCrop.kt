package com.movie.scanner.util

import android.graphics.Bitmap

/**
 * Center-crops a bitmap to the aspect ratio of a target view (same geometry as fill-center preview).
 */
/**
 * Pixel size and origin for a fill-center crop inside a source bitmap.
 */
internal data class FillCenterCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

object BitmapFillCenterCrop {
    /**
     * Computes the centered crop rectangle for [bitmapWidth]×[bitmapHeight] to match [targetWidth]:[targetHeight].
     */
    internal fun computeFillCenterCropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): FillCenterCropRect? {
        if (targetWidth <= 0 || targetHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null
        }
        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val cropWidth: Int
        val cropHeight: Int
        if (bitmapAspect > targetAspect) {
            cropHeight = bitmapHeight
            cropWidth = (bitmapHeight * targetAspect).toInt().coerceAtMost(bitmapWidth)
        } else {
            cropWidth = bitmapWidth
            cropHeight = (bitmapWidth / targetAspect).toInt().coerceAtMost(bitmapHeight)
        }
        if (cropWidth == bitmapWidth && cropHeight == bitmapHeight) {
            return null
        }
        return FillCenterCropRect(
            left = (bitmapWidth - cropWidth) / 2,
            top = (bitmapHeight - cropHeight) / 2,
            width = cropWidth,
            height = cropHeight,
        )
    }

    /**
     * Returns [bitmap] unchanged when [targetWidth] or [targetHeight] is non-positive or crop would be a no-op.
     */
    fun cropToViewAspectRatio(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val cropRect = computeFillCenterCropRect(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        ) ?: return bitmap
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width,
            cropRect.height,
        )
        if (croppedBitmap !== bitmap) {
            bitmap.recycle()
        }
        return croppedBitmap
    }
}
