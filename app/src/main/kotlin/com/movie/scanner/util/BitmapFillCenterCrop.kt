package com.movie.scanner.util

import android.graphics.Bitmap

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

    /**
     * Removes rows that sit behind top and bottom preview chrome on a bitmap already cropped to the full preview aspect.
     */
    internal fun computeVerticalInsetCropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        viewHeightPx: Int,
        topInsetPx: Int,
        bottomInsetPx: Int,
    ): FillCenterCropRect? {
        if (
            viewHeightPx <= 0 ||
            bitmapWidth <= 0 ||
            bitmapHeight <= 0 ||
            topInsetPx <= 0 && bottomInsetPx <= 0
        ) {
            return null
        }
        val top = (bitmapHeight.toFloat() * topInsetPx / viewHeightPx).toInt().coerceIn(0, bitmapHeight - 1)
        val bottomCrop = (bitmapHeight.toFloat() * bottomInsetPx / viewHeightPx).toInt().coerceIn(0, bitmapHeight - top - 1)
        val cropHeight = bitmapHeight - top - bottomCrop
        if (cropHeight <= 0 || cropHeight == bitmapHeight) {
            return null
        }
        return FillCenterCropRect(
            left = 0,
            top = top,
            width = bitmapWidth,
            height = cropHeight,
        )
    }

    /**
     * Crops [bitmap] to the preview region visible between top and bottom UI chrome.
     */
    fun cropToVisiblePreview(
        bitmap: Bitmap,
        viewWidth: Int,
        viewHeight: Int,
        topInsetPx: Int,
        bottomInsetPx: Int,
    ): Bitmap {
        val aspectCropped = cropToViewAspectRatio(bitmap, viewWidth, viewHeight)
        val insetRect = computeVerticalInsetCropRect(
            bitmapWidth = aspectCropped.width,
            bitmapHeight = aspectCropped.height,
            viewHeightPx = viewHeight,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
        ) ?: return aspectCropped
        val croppedBitmap = Bitmap.createBitmap(
            aspectCropped,
            insetRect.left,
            insetRect.top,
            insetRect.width,
            insetRect.height,
        )
        if (croppedBitmap !== aspectCropped) {
            aspectCropped.recycle()
        }
        return croppedBitmap
    }
}
