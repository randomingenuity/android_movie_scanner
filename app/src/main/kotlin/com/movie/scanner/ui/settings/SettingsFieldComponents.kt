package com.movie.scanner.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import com.movie.scanner.data.model.FieldValidationStatus
import com.movie.scanner.data.model.ValidatedField

@Composable
fun ValidatedApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: ValidatedField,
    onBlurValidate: () -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = visualTransformation,
        isError = field.status == FieldValidationStatus.INVALID,
        supportingText = {
            field.errorMessage?.let { errorMessage ->
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }
        },
        trailingIcon = {
            ValidationStatusIcon(status = field.status)
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    onBlurValidate()
                }
            },
    )
}

@Composable
private fun ValidationStatusIcon(status: FieldValidationStatus) {
    when (status) {
        FieldValidationStatus.VALIDATING -> {
            CircularProgressIndicator()
        }
        FieldValidationStatus.VALID -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Valid",
                tint = Color(0xFF2E7D32),
            )
        }
        FieldValidationStatus.INVALID -> {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Invalid",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        FieldValidationStatus.IDLE -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDropdownField(
    label: String,
    selectedModel: String,
    models: List<String>,
    enabled: Boolean,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { isExpanded ->
            if (enabled) {
                expanded = isExpanded
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdownField(
    label: String,
    selectedCode: String,
    languages: List<com.movie.scanner.data.model.TmdbLanguageOption>,
    enabled: Boolean,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = when {
        selectedCode.isBlank() -> "Device default"
        else -> languages.firstOrNull { language -> language.code == selectedCode }?.displayName ?: selectedCode
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { isExpanded ->
            if (enabled) {
                expanded = isExpanded
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Device default") },
                onClick = {
                    onLanguageSelected("")
                    expanded = false
                },
            )
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text("${language.displayName} (${language.code})") },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
