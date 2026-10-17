package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitmapFillCenterCropTest {
    @Test
    fun computeFillCenterCropRect_wideBitmap_cropsHorizontalMargins() {
        val cropRect = BitmapFillCenterCrop.computeFillCenterCropRect(
            bitmapWidth = 400,
            bitmapHeight = 200,
            targetWidth = 100,
            targetHeight = 100,
        )
        assertEquals(FillCenterCropRect(left = 100, top = 0, width = 200, height = 200), cropRect)
    }

    @Test
    fun computeFillCenterCropRect_tallBitmap_cropsVerticalMargins() {
        val cropRect = BitmapFillCenterCrop.computeFillCenterCropRect(
            bitmapWidth = 200,
            bitmapHeight = 400,
            targetWidth = 100,
            targetHeight = 100,
        )
        assertEquals(FillCenterCropRect(left = 0, top = 100, width = 200, height = 200), cropRect)
    }

    @Test
    fun computeFillCenterCropRect_invalidTarget_returnsNull() {
        assertNull(
            BitmapFillCenterCrop.computeFillCenterCropRect(
                bitmapWidth = 10,
                bitmapHeight = 10,
                targetWidth = 0,
                targetHeight = 100,
            ),
        )
    }

    @Test
    fun computeVerticalInsetCropRect_removesTopAndBottomChromeBands() {
        val cropRect = BitmapFillCenterCrop.computeVerticalInsetCropRect(
            bitmapWidth = 1080,
            bitmapHeight = 1000,
            viewHeightPx = 2000,
            topInsetPx = 200,
            bottomInsetPx = 300,
        )
        assertEquals(FillCenterCropRect(left = 0, top = 100, width = 1080, height = 750), cropRect)
    }
}
