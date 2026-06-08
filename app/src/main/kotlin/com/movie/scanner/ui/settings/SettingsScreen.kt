package com.movie.scanner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movie.scanner.data.model.FieldValidationStatus
import com.movie.scanner.data.model.LlmProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onConfigured: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.geminiModelBumpedToast) {
        uiState.geminiModelBumpedToast?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeGeminiModelBumpedToast()
        }
    }
    val geminiModelsEnabled = uiState.geminiField.status == FieldValidationStatus.VALID
    val openAiModelsEnabled = uiState.openAiField.status == FieldValidationStatus.VALID
    val claudeModelsEnabled = uiState.claudeField.status == FieldValidationStatus.VALID
    val tmdbLanguagesEnabled = uiState.tmdbField.status == FieldValidationStatus.VALID

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Bring your own API keys. Keys are stored encrypted on this device and are not backed up.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ValidatedApiKeyField(
                value = uiState.geminiApiKey,
                onValueChange = viewModel::updateGeminiApiKey,
                label = "Gemini API key",
                field = uiState.geminiField,
                onBlurValidate = viewModel::validateGeminiField,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (uiState.geminiApiKey.isNotBlank()) {
                ModelDropdownField(
                    label = "Gemini model",
                    selectedModel = uiState.geminiModel,
                    models = uiState.geminiModels,
                    enabled = geminiModelsEnabled,
                    onModelSelected = viewModel::updateGeminiModel,
                )
            }
            ValidatedApiKeyField(
                value = uiState.openAiApiKey,
                onValueChange = viewModel::updateOpenAiApiKey,
                label = "OpenAI API key",
                field = uiState.openAiField,
                onBlurValidate = viewModel::validateOpenAiField,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (uiState.openAiApiKey.isNotBlank()) {
                ModelDropdownField(
                    label = "OpenAI model",
                    selectedModel = uiState.openAiModel,
                    models = uiState.openAiModels,
                    enabled = openAiModelsEnabled,
                    onModelSelected = viewModel::updateOpenAiModel,
                )
            }
            ValidatedApiKeyField(
                value = uiState.claudeApiKey,
                onValueChange = viewModel::updateClaudeApiKey,
                label = "Claude API key",
                field = uiState.claudeField,
                onBlurValidate = viewModel::validateClaudeField,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (uiState.claudeApiKey.isNotBlank()) {
                ModelDropdownField(
                    label = "Claude model",
                    selectedModel = uiState.claudeModel,
                    models = uiState.claudeModels,
                    enabled = claudeModelsEnabled,
                    onModelSelected = viewModel::updateClaudeModel,
                )
            }
            ValidatedApiKeyField(
                value = uiState.tmdbApiKey,
                onValueChange = viewModel::updateTmdbApiKey,
                label = "TMDB API key",
                field = uiState.tmdbField,
                onBlurValidate = viewModel::validateTmdbField,
                visualTransformation = PasswordVisualTransformation(),
            )
            LanguageDropdownField(
                label = "TMDB language override",
                selectedCode = uiState.tmdbLanguageOverride,
                languages = uiState.tmdbLanguages,
                enabled = tmdbLanguagesEnabled,
                onLanguageSelected = viewModel::updateTmdbLanguageOverride,
            )
            ProviderDropdownField(
                label = "Preferred LLM",
                selectedProviderName = uiState.preferredLlmProvider.displayName,
                providerNames = LlmProvider.entries.map { provider -> provider.displayName },
                onProviderSelected = { providerName ->
                    val provider = LlmProvider.entries.first { entry -> entry.displayName == providerName }
                    viewModel.updatePreferredProvider(provider)
                },
            )
            uiState.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            uiState.statusMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = {
                    viewModel.saveSettings {
                        if (viewModel.hasMinimumConfiguration()) {
                            onConfigured()
                        }
                    }
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save settings")
            }
        }
    }
}
