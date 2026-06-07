package com.movie.scanner.util

import com.movie.scanner.data.local.ApiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiModelSelectorTest {
    @Test
    fun selectPreferredModel_prefersCurrentSelectionWhenAvailable() {
        val selected = GeminiModelSelector.selectPreferredModel(
            availableModels = listOf("gemini-2.0-flash", "gemini-1.5-flash"),
            persistedModel = "gemini-1.5-flash",
            currentSelection = "gemini-2.0-flash",
        )

        assertEquals("gemini-2.0-flash", selected)
    }

    @Test
    fun selectPreferredModel_usesPersistedModelWhenCurrentSelectionMissing() {
        val selected = GeminiModelSelector.selectPreferredModel(
            availableModels = listOf("gemini-2.0-flash", "gemini-1.5-flash"),
            persistedModel = "gemini-1.5-flash",
            currentSelection = "gemini-9.9-flash",
        )

        assertEquals("gemini-1.5-flash", selected)
    }

    @Test
    fun selectLatestFlashModel_picksHighestVersion() {
        val selected = GeminiModelSelector.selectLatestFlashModel(
            listOf(
                "gemini-1.5-flash",
                "gemini-2.0-flash",
                "gemini-2.5-flash",
                "gemini-pro",
            ),
        )

        assertEquals("gemini-2.5-flash", selected)
    }

    @Test
    fun selectPreferredModel_fallsBackToLatestFlashModel() {
        val selected = GeminiModelSelector.selectPreferredModel(
            availableModels = listOf("gemini-2.0-flash", "gemini-pro"),
            persistedModel = "gemini-1.5-flash",
            currentSelection = "",
        )

        assertEquals("gemini-2.0-flash", selected)
    }

    @Test
    fun selectPreferredModel_usesStoreFallbackWhenNoModelsAvailable() {
        val selected = GeminiModelSelector.selectPreferredModel(
            availableModels = emptyList(),
            persistedModel = null,
            currentSelection = "",
        )

        assertEquals(ApiKeyStore.FALLBACK_GEMINI_MODEL, selected)
    }
}
