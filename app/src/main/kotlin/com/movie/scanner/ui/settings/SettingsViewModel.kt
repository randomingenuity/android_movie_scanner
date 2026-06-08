package com.movie.scanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.FieldValidationStatus
import com.movie.scanner.data.model.LlmProvider
import com.movie.scanner.data.model.TmdbLanguageOption
import com.movie.scanner.data.model.ValidatedField
import com.movie.scanner.data.repository.SettingsValidationRepository
import com.movie.scanner.util.GeminiModelSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val claudeApiKey: String = "",
    val tmdbApiKey: String = "",
    val preferredLlmProvider: LlmProvider = LlmProvider.GEMINI,
    val geminiModel: String = "",
    val openAiModel: String = ApiKeyStore.DEFAULT_OPENAI_MODEL,
    val claudeModel: String = ApiKeyStore.DEFAULT_CLAUDE_MODEL,
    val tmdbLanguageOverride: String = "",
    val geminiField: ValidatedField = ValidatedField(),
    val openAiField: ValidatedField = ValidatedField(),
    val claudeField: ValidatedField = ValidatedField(),
    val tmdbField: ValidatedField = ValidatedField(),
    val geminiModels: List<String> = emptyList(),
    val openAiModels: List<String> = emptyList(),
    val claudeModels: List<String> = emptyList(),
    val tmdbLanguages: List<TmdbLanguageOption> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val geminiModelBumpedToast: String? = null,
    val isSaving: Boolean = false,
)

private data class GeminiModelSelection(
    val selectedModel: String,
    val bumpedToast: String?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val settingsValidationRepository: SettingsValidationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            validateStoredKeysOnOpen()
        }
    }

    fun updateGeminiApiKey(value: String) {
        _uiState.update {
            it.copy(
                geminiApiKey = value,
                geminiField = ValidatedField(),
                geminiModels = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun updateOpenAiApiKey(value: String) {
        _uiState.update {
            it.copy(
                openAiApiKey = value,
                openAiField = ValidatedField(),
                openAiModels = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun updateClaudeApiKey(value: String) {
        _uiState.update {
            it.copy(
                claudeApiKey = value,
                claudeField = ValidatedField(),
                claudeModels = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun updateTmdbApiKey(value: String) {
        _uiState.update {
            it.copy(
                tmdbApiKey = value,
                tmdbField = ValidatedField(),
                tmdbLanguages = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun updatePreferredProvider(provider: LlmProvider) {
        _uiState.update { it.copy(preferredLlmProvider = provider) }
    }

    fun updateGeminiModel(value: String) {
        _uiState.update { it.copy(geminiModel = value) }
    }

    fun updateOpenAiModel(value: String) {
        _uiState.update { it.copy(openAiModel = value) }
    }

    fun updateClaudeModel(value: String) {
        _uiState.update { it.copy(claudeModel = value) }
    }

    fun updateTmdbLanguageOverride(value: String) {
        _uiState.update { it.copy(tmdbLanguageOverride = value) }
    }

    fun consumeGeminiModelBumpedToast() {
        _uiState.update { it.copy(geminiModelBumpedToast = null) }
    }

    fun validateGeminiField() {
        val apiKey = _uiState.value.geminiApiKey
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    geminiField = ValidatedField(status = FieldValidationStatus.IDLE),
                    geminiModels = emptyList(),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(geminiField = ValidatedField(status = FieldValidationStatus.VALIDATING))
            }
            val result = settingsValidationRepository.validateGeminiApiKey(apiKey)
            _uiState.update { state ->
                if (result.isSuccess) {
                    val models = result.getOrThrow().models
                    val selection = resolveGeminiModelSelection(models, state.geminiModel)
                    state.copy(
                        geminiField = ValidatedField(status = FieldValidationStatus.VALID),
                        geminiModels = models,
                        geminiModel = selection.selectedModel,
                        geminiModelBumpedToast = selection.bumpedToast,
                    )
                } else {
                    state.copy(
                        geminiField = ValidatedField(
                            status = FieldValidationStatus.INVALID,
                            errorMessage = result.exceptionOrNull()?.message,
                        ),
                        geminiModels = emptyList(),
                    )
                }
            }
        }
    }

    fun validateOpenAiField() {
        val apiKey = _uiState.value.openAiApiKey
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    openAiField = ValidatedField(status = FieldValidationStatus.IDLE),
                    openAiModels = emptyList(),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(openAiField = ValidatedField(status = FieldValidationStatus.VALIDATING))
            }
            val result = settingsValidationRepository.validateOpenAiApiKey(apiKey)
            _uiState.update { state ->
                if (result.isSuccess) {
                    val models = result.getOrThrow().models
                    val selectedModel = resolveOpenAiModelSelection(models, state.openAiModel)
                    state.copy(
                        openAiField = ValidatedField(status = FieldValidationStatus.VALID),
                        openAiModels = models,
                        openAiModel = selectedModel,
                    )
                } else {
                    state.copy(
                        openAiField = ValidatedField(
                            status = FieldValidationStatus.INVALID,
                            errorMessage = result.exceptionOrNull()?.message,
                        ),
                        openAiModels = emptyList(),
                    )
                }
            }
        }
    }

    fun validateClaudeField() {
        val apiKey = _uiState.value.claudeApiKey
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    claudeField = ValidatedField(status = FieldValidationStatus.IDLE),
                    claudeModels = emptyList(),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(claudeField = ValidatedField(status = FieldValidationStatus.VALIDATING))
            }
            val result = settingsValidationRepository.validateClaudeApiKey(apiKey)
            _uiState.update { state ->
                if (result.isSuccess) {
                    val models = result.getOrThrow().models
                    val selectedModel = resolveClaudeModelSelection(models, state.claudeModel)
                    state.copy(
                        claudeField = ValidatedField(status = FieldValidationStatus.VALID),
                        claudeModels = models,
                        claudeModel = selectedModel,
                    )
                } else {
                    state.copy(
                        claudeField = ValidatedField(
                            status = FieldValidationStatus.INVALID,
                            errorMessage = result.exceptionOrNull()?.message,
                        ),
                        claudeModels = emptyList(),
                    )
                }
            }
        }
    }

    fun validateTmdbField() {
        val apiKey = _uiState.value.tmdbApiKey
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    tmdbField = ValidatedField(status = FieldValidationStatus.IDLE),
                    tmdbLanguages = emptyList(),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(tmdbField = ValidatedField(status = FieldValidationStatus.VALIDATING))
            }
            val result = settingsValidationRepository.validateTmdbApiKey(apiKey)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        tmdbField = ValidatedField(status = FieldValidationStatus.VALID),
                        tmdbLanguages = result.getOrThrow().languages,
                    )
                } else {
                    state.copy(
                        tmdbField = ValidatedField(
                            status = FieldValidationStatus.INVALID,
                            errorMessage = result.exceptionOrNull()?.message,
                        ),
                        tmdbLanguages = emptyList(),
                    )
                }
            }
        }
    }

    fun validateAllConfiguredFields() {
        validateGeminiField()
        validateOpenAiField()
        validateClaudeField()
        validateTmdbField()
    }

    fun saveSettings(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null, errorMessage = null) }
            validateAllConfiguredFieldsSynchronously()
            val state = _uiState.value
            if (state.tmdbApiKey.isBlank()) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "TMDB API key is required.")
                }
                return@launch
            }
            if (state.tmdbField.status != FieldValidationStatus.VALID) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = state.tmdbField.errorMessage ?: "TMDB API key must pass validation.",
                    )
                }
                return@launch
            }
            val hasGemini = state.geminiApiKey.isNotBlank()
            val hasOpenAi = state.openAiApiKey.isNotBlank()
            val hasClaude = state.claudeApiKey.isNotBlank()
            if (!hasGemini && !hasOpenAi && !hasClaude) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Configure at least one LLM API key.")
                }
                return@launch
            }
            if (hasGemini && state.geminiField.status != FieldValidationStatus.VALID) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = state.geminiField.errorMessage ?: "Gemini API key must pass validation.",
                    )
                }
                return@launch
            }
            if (hasOpenAi && state.openAiField.status != FieldValidationStatus.VALID) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = state.openAiField.errorMessage ?: "OpenAI API key must pass validation.",
                    )
                }
                return@launch
            }
            if (hasClaude && state.claudeField.status != FieldValidationStatus.VALID) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = state.claudeField.errorMessage ?: "Claude API key must pass validation.",
                    )
                }
                return@launch
            }
            val geminiModelToSave = if (hasGemini) {
                state.geminiModel.ifBlank {
                    GeminiModelSelector.selectLatestFlashModel(state.geminiModels)
                        ?: ApiKeyStore.FALLBACK_GEMINI_MODEL
                }
            } else {
                null
            }
            val openAiModelToSave = if (hasOpenAi) {
                state.openAiModel.ifBlank { ApiKeyStore.DEFAULT_OPENAI_MODEL }
            } else {
                null
            }
            val claudeModelToSave = if (hasClaude) {
                state.claudeModel.ifBlank { ApiKeyStore.DEFAULT_CLAUDE_MODEL }
            } else {
                null
            }
            if (hasGemini) {
                apiKeyStore.saveGeminiApiKey(state.geminiApiKey)
                apiKeyStore.saveGeminiModel(geminiModelToSave!!)
            }
            if (hasOpenAi) {
                apiKeyStore.saveOpenAiApiKey(state.openAiApiKey)
                apiKeyStore.saveOpenAiModel(openAiModelToSave!!)
            }
            if (hasClaude) {
                apiKeyStore.saveClaudeApiKey(state.claudeApiKey)
                apiKeyStore.saveClaudeModel(claudeModelToSave!!)
            }
            apiKeyStore.saveTmdbApiKey(state.tmdbApiKey)
            apiKeyStore.savePreferredLlmProvider(state.preferredLlmProvider)
            apiKeyStore.saveTmdbLanguageOverride(state.tmdbLanguageOverride.ifBlank { null })
            _uiState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = "Settings saved.",
                )
            }
            onSuccess()
        }
    }

    fun hasMinimumConfiguration(): Boolean = apiKeyStore.hasMinimumConfiguration()

    private suspend fun validateStoredKeysOnOpen() {
        val state = _uiState.value
        if (state.geminiApiKey.isNotBlank()) {
            validateGeminiFieldAndAwait()
        }
        if (state.openAiApiKey.isNotBlank()) {
            validateOpenAiFieldAndAwait()
        }
        if (state.claudeApiKey.isNotBlank()) {
            validateClaudeFieldAndAwait()
        }
        if (state.tmdbApiKey.isNotBlank()) {
            validateTmdbFieldAndAwait()
        }
    }

    private suspend fun validateAllConfiguredFieldsSynchronously() {
        val state = _uiState.value
        if (state.geminiApiKey.isNotBlank()) {
            validateGeminiFieldAndAwait()
        }
        if (state.openAiApiKey.isNotBlank()) {
            validateOpenAiFieldAndAwait()
        }
        if (state.claudeApiKey.isNotBlank()) {
            validateClaudeFieldAndAwait()
        }
        if (state.tmdbApiKey.isNotBlank()) {
            validateTmdbFieldAndAwait()
        }
    }

    private suspend fun validateGeminiFieldAndAwait() {
        val apiKey = _uiState.value.geminiApiKey
        if (apiKey.isBlank()) {
            return
        }
        _uiState.update {
            it.copy(geminiField = ValidatedField(status = FieldValidationStatus.VALIDATING))
        }
        val result = settingsValidationRepository.validateGeminiApiKey(apiKey)
        _uiState.update { state ->
            if (result.isSuccess) {
                val models = result.getOrThrow().models
                val selection = resolveGeminiModelSelection(models, state.geminiModel)
                state.copy(
                    geminiField = ValidatedField(status = FieldValidationStatus.VALID),
                    geminiModels = models,
                    geminiModel = selection.selectedModel,
                    geminiModelBumpedToast = selection.bumpedToast,
                )
            } else {
                state.copy(
                    geminiField = ValidatedField(
                        status = FieldValidationStatus.INVALID,
                        errorMessage = result.exceptionOrNull()?.message,
                    ),
                    geminiModels = emptyList(),
                )
            }
        }
    }

    private fun resolveGeminiModelSelection(
        availableModels: List<String>,
        currentSelection: String,
    ): GeminiModelSelection {
        val persistedModel = apiKeyStore.getGeminiModel()
        val selectedModel = GeminiModelSelector.selectPreferredModel(
            availableModels = availableModels,
            persistedModel = persistedModel,
            currentSelection = currentSelection,
        )
        val bumpedToast = if (!persistedModel.isNullOrBlank() && persistedModel !in availableModels) {
            "Saved Gemini model $persistedModel is no longer available. Bumped to $selectedModel."
        } else {
            null
        }
        return GeminiModelSelection(
            selectedModel = selectedModel,
            bumpedToast = bumpedToast,
        )
    }

    private suspend fun validateOpenAiFieldAndAwait() {
        val apiKey = _uiState.value.openAiApiKey
        if (apiKey.isBlank()) {
            return
        }
        _uiState.update {
            it.copy(openAiField = ValidatedField(status = FieldValidationStatus.VALIDATING))
        }
        val result = settingsValidationRepository.validateOpenAiApiKey(apiKey)
        _uiState.update { state ->
            if (result.isSuccess) {
                val models = result.getOrThrow().models
                val selectedModel = resolveOpenAiModelSelection(models, state.openAiModel)
                state.copy(
                    openAiField = ValidatedField(status = FieldValidationStatus.VALID),
                    openAiModels = models,
                    openAiModel = selectedModel,
                )
            } else {
                state.copy(
                    openAiField = ValidatedField(
                        status = FieldValidationStatus.INVALID,
                        errorMessage = result.exceptionOrNull()?.message,
                    ),
                    openAiModels = emptyList(),
                )
            }
        }
    }

    private suspend fun validateClaudeFieldAndAwait() {
        val apiKey = _uiState.value.claudeApiKey
        if (apiKey.isBlank()) {
            return
        }
        _uiState.update {
            it.copy(claudeField = ValidatedField(status = FieldValidationStatus.VALIDATING))
        }
        val result = settingsValidationRepository.validateClaudeApiKey(apiKey)
        _uiState.update { state ->
            if (result.isSuccess) {
                val models = result.getOrThrow().models
                val selectedModel = resolveClaudeModelSelection(models, state.claudeModel)
                state.copy(
                    claudeField = ValidatedField(status = FieldValidationStatus.VALID),
                    claudeModels = models,
                    claudeModel = selectedModel,
                )
            } else {
                state.copy(
                    claudeField = ValidatedField(
                        status = FieldValidationStatus.INVALID,
                        errorMessage = result.exceptionOrNull()?.message,
                    ),
                    claudeModels = emptyList(),
                )
            }
        }
    }

    private suspend fun validateTmdbFieldAndAwait() {
        val apiKey = _uiState.value.tmdbApiKey
        if (apiKey.isBlank()) {
            return
        }
        _uiState.update {
            it.copy(tmdbField = ValidatedField(status = FieldValidationStatus.VALIDATING))
        }
        val result = settingsValidationRepository.validateTmdbApiKey(apiKey)
        _uiState.update { state ->
            if (result.isSuccess) {
                state.copy(
                    tmdbField = ValidatedField(status = FieldValidationStatus.VALID),
                    tmdbLanguages = result.getOrThrow().languages,
                )
            } else {
                state.copy(
                    tmdbField = ValidatedField(
                        status = FieldValidationStatus.INVALID,
                        errorMessage = result.exceptionOrNull()?.message,
                    ),
                    tmdbLanguages = emptyList(),
                )
            }
        }
    }

    private fun resolveOpenAiModelSelection(
        models: List<String>,
        currentSelection: String,
    ): String = when {
        currentSelection in models -> currentSelection
        ApiKeyStore.DEFAULT_OPENAI_MODEL in models -> ApiKeyStore.DEFAULT_OPENAI_MODEL
        else -> models.first()
    }

    private fun resolveClaudeModelSelection(
        models: List<String>,
        currentSelection: String,
    ): String = when {
        currentSelection in models -> currentSelection
        ApiKeyStore.DEFAULT_CLAUDE_MODEL in models -> ApiKeyStore.DEFAULT_CLAUDE_MODEL
        else -> models.first()
    }

    private fun loadState(): SettingsUiState = SettingsUiState(
        geminiApiKey = apiKeyStore.getGeminiApiKey().orEmpty(),
        openAiApiKey = apiKeyStore.getOpenAiApiKey().orEmpty(),
        claudeApiKey = apiKeyStore.getClaudeApiKey().orEmpty(),
        tmdbApiKey = apiKeyStore.getTmdbApiKey().orEmpty(),
        preferredLlmProvider = apiKeyStore.getPreferredLlmProvider(),
        geminiModel = apiKeyStore.getGeminiModel().orEmpty(),
        openAiModel = apiKeyStore.getOpenAiModel(),
        claudeModel = apiKeyStore.getClaudeModel(),
        tmdbLanguageOverride = apiKeyStore.getTmdbLanguageOverride().orEmpty(),
    )
}
