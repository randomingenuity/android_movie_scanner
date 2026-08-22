package com.movie.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscTypeTest {
    @Test
    fun options_areSortedByLabel() {
        assertEquals(
            listOf(
                "3D Blu-Ray",
                "4K Blu-Ray",
                "Blu-Ray",
                "DVD",
                "HD DVD",
            ),
            DiscType.options.map { discType -> discType.label },
        )
    }

    @Test
    fun labelForStored_returnsLabelForCanonicalValue() {
        assertEquals("Blu-Ray", DiscType.labelForStored("bluray"))
        assertEquals("DVD", DiscType.labelForStored("dvd"))
        assertEquals("4K Blu-Ray", DiscType.labelForStored("4k_bluray"))
        assertEquals("3D Blu-Ray", DiscType.labelForStored("3d_bluray"))
        assertEquals("HD DVD", DiscType.labelForStored("hd_dvd"))
    }

    @Test
    fun labelForStored_returnsLabelForLegacyStoredLabel() {
        assertEquals("Blu-Ray", DiscType.labelForStored("Blu-Ray"))
        assertEquals("DVD", DiscType.labelForStored("DVD"))
    }

    @Test
    fun fromStored_resolvesValueAndLegacyLabel() {
        assertEquals(DiscType.BLURAY, DiscType.fromStored("bluray"))
        assertEquals(DiscType.DVD, DiscType.fromStored("DVD"))
        assertNull(DiscType.fromStored("VHS"))
    }
}
