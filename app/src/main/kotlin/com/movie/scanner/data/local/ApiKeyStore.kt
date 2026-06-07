package com.movie.scanner.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.movie.scanner.data.model.LlmProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getGeminiApiKey(): String? = preferences.getString(KEY_GEMINI, null)?.takeIf { it.isNotEmpty() }

    fun getOpenAiApiKey(): String? = preferences.getString(KEY_OPENAI, null)?.takeIf { it.isNotEmpty() }

    fun getTmdbApiKey(): String? = preferences.getString(KEY_TMDB, null)?.takeIf { it.isNotEmpty() }

    fun getPreferredLlmProvider(): LlmProvider {
        val stored = preferences.getString(KEY_PREFERRED_LLM, LlmProvider.GEMINI.name)
        return runCatching { LlmProvider.valueOf(stored ?: LlmProvider.GEMINI.name) }
            .getOrDefault(LlmProvider.GEMINI)
    }

    fun getGeminiModel(): String? =
        preferences.getString(KEY_GEMINI_MODEL, null)?.takeIf { it.isNotEmpty() }

    fun resolveGeminiModel(): String = getGeminiModel() ?: FALLBACK_GEMINI_MODEL

    fun getOpenAiModel(): String =
        preferences.getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL) ?: DEFAULT_OPENAI_MODEL

    fun getTmdbLanguageOverride(): String? =
        preferences.getString(KEY_TMDB_LANGUAGE, null)?.takeIf { it.isNotEmpty() }

    fun saveGeminiApiKey(value: String) {
        preferences.edit().putString(KEY_GEMINI, value).commit()
    }

    fun saveOpenAiApiKey(value: String) {
        preferences.edit().putString(KEY_OPENAI, value).commit()
    }

    fun saveTmdbApiKey(value: String) {
        preferences.edit().putString(KEY_TMDB, value).commit()
    }

    fun savePreferredLlmProvider(provider: LlmProvider) {
        preferences.edit().putString(KEY_PREFERRED_LLM, provider.name).commit()
    }

    fun saveGeminiModel(model: String) {
        preferences.edit().putString(KEY_GEMINI_MODEL, model).commit()
    }

    fun saveOpenAiModel(model: String) {
        preferences.edit().putString(KEY_OPENAI_MODEL, model).commit()
    }

    fun saveTmdbLanguageOverride(language: String?) {
        if (language.isNullOrBlank()) {
            preferences.edit().remove(KEY_TMDB_LANGUAGE).commit()
        } else {
            preferences.edit().putString(KEY_TMDB_LANGUAGE, language).commit()
        }
    }

    fun hasMinimumConfiguration(): Boolean {
        val hasTmdb = !getTmdbApiKey().isNullOrBlank()
        val hasLlm = !getGeminiApiKey().isNullOrBlank() || !getOpenAiApiKey().isNullOrBlank()
        return hasTmdb && hasLlm
    }

    fun hasLlmProvider(provider: LlmProvider): Boolean = when (provider) {
        LlmProvider.GEMINI -> !getGeminiApiKey().isNullOrBlank()
        LlmProvider.OPENAI -> !getOpenAiApiKey().isNullOrBlank()
    }

    companion object {
        private const val PREFERENCES_NAME = "movie_scanner_secure_prefs"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_OPENAI = "openai_api_key"
        private const val KEY_TMDB = "tmdb_api_key"
        private const val KEY_PREFERRED_LLM = "preferred_llm_provider"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_TMDB_LANGUAGE = "tmdb_language_override"
        const val FALLBACK_GEMINI_MODEL: String = "gemini-2.5-flash"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
    }
}
