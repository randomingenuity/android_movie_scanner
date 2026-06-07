package com.movie.scanner.util

import com.movie.scanner.data.local.ApiKeyStore

object GeminiModelSelector {
    private val flashModelPattern = Regex("^gemini-([0-9]+(?:\\.[0-9]+)*)-flash$")

    fun selectPreferredModel(
        availableModels: List<String>,
        persistedModel: String?,
        currentSelection: String = "",
    ): String {
        if (currentSelection.isNotBlank() && currentSelection in availableModels) {
            return currentSelection
        }
        if (!persistedModel.isNullOrBlank() && persistedModel in availableModels) {
            return persistedModel
        }
        return selectLatestFlashModel(availableModels)
            ?: availableModels.firstOrNull()
            ?: ApiKeyStore.FALLBACK_GEMINI_MODEL
    }

    fun selectLatestFlashModel(availableModels: List<String>): String? {
        return availableModels
            .mapNotNull { modelName ->
                val match = flashModelPattern.matchEntire(modelName) ?: return@mapNotNull null
                modelName to parseVersionParts(match.groupValues[1])
            }
            .maxWithOrNull { left, right ->
                compareVersionParts(left.second, right.second)
            }
            ?.first
    }

    private fun parseVersionParts(version: String): List<Int> =
        version.split('.').map { part -> part.toIntOrNull() ?: 0 }

    private fun compareVersionParts(left: List<Int>, right: List<Int>): Int {
        val maxLength = maxOf(left.size, right.size)
        for (index in 0 until maxLength) {
            val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }
}
