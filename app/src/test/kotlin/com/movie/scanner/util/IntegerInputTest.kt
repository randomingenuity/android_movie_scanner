package com.movie.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntegerInputTest {
    @Test
    fun filterDigits_removesNonDigits() {
        assertEquals("123", IntegerInput.filterDigits("12a3b"))
    }

    @Test
    fun parseOptionalInt_returnsNullForBlank() {
        assertNull(IntegerInput.parseOptionalInt(""))
    }

    @Test
    fun parseOptionalInt_parsesWholeNumber() {
        assertEquals(42, IntegerInput.parseOptionalInt("42"))
    }
}
