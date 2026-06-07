package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeDecoderTest {
    @Test
    fun buildBarcodeLabel_recognizesIsbnPrefixes() {
        assertEquals("ISBN", BarcodeDecoder.buildBarcodeLabel("9781234567890"))
        assertEquals("ISBN", BarcodeDecoder.buildBarcodeLabel("9791234567890"))
    }

    @Test
    fun buildBarcodeLabel_recognizesIssnPrefix() {
        assertEquals("ISSN", BarcodeDecoder.buildBarcodeLabel("9771234567890"))
    }

    @Test
    fun buildBarcodeLabel_defaultsToBarcode() {
        assertEquals("Barcode", BarcodeDecoder.buildBarcodeLabel("012345678905"))
    }

    @Test
    fun formatBarcodeLine_usesDigitsForDisplay() {
        assertEquals("ISBN: 9781234567890", BarcodeDecoder.formatBarcodeLine("978-1-234567890"))
    }

    @Test
    fun formatBarcodeLine_fallsBackToRawValueWhenNoDigits() {
        assertEquals("Barcode: ABC", BarcodeDecoder.formatBarcodeLine("ABC"))
    }
}
