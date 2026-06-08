package com.movie.scanner.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@Composable
fun ModelDropdownField(
    label: String,
    selectedModel: String,
    models: List<String>,
    enabled: Boolean,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReadOnlyDropdownField(
        label = label,
        value = selectedModel,
        enabled = enabled,
        modifier = modifier,
    ) { dismissMenu ->
        models.forEach { model ->
            DropdownMenuItem(
                text = { Text(model) },
                onClick = {
                    onModelSelected(model)
                    dismissMenu()
                },
            )
        }
    }
}

@Composable
fun LanguageDropdownField(
    label: String,
    selectedCode: String,
    languages: List<com.movie.scanner.data.model.TmdbLanguageOption>,
    enabled: Boolean,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayValue = when {
        selectedCode.isBlank() -> "Device default"
        else -> languages.firstOrNull { language -> language.code == selectedCode }?.displayName ?: selectedCode
    }
    ReadOnlyDropdownField(
        label = label,
        value = displayValue,
        enabled = enabled,
        modifier = modifier,
    ) { dismissMenu ->
        DropdownMenuItem(
            text = { Text("Device default") },
            onClick = {
                onLanguageSelected("")
                dismissMenu()
            },
        )
        languages.forEach { language ->
            DropdownMenuItem(
                text = { Text("${language.displayName} (${language.code})") },
                onClick = {
                    onLanguageSelected(language.code)
                    dismissMenu()
                },
            )
        }
    }
}

@Composable
fun ProviderDropdownField(
    label: String,
    selectedProviderName: String,
    providerNames: List<String>,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReadOnlyDropdownField(
        label = label,
        value = selectedProviderName,
        enabled = true,
        modifier = modifier,
    ) { dismissMenu ->
        providerNames.forEach { providerName ->
            DropdownMenuItem(
                text = { Text(providerName) },
                onClick = {
                    onProviderSelected(providerName)
                    dismissMenu()
                },
            )
        }
    }
}

@Composable
private fun ReadOnlyDropdownField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    menuItems: @Composable (dismissMenu: () -> Unit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val menuInteractionSource = remember { MutableInteractionSource() }
    val dismissMenu = { menuExpanded = false }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = menuInteractionSource,
                        indication = null,
                        onClick = { menuExpanded = true },
                    ),
            )
        }
        DropdownMenu(
            expanded = menuExpanded && enabled,
            onDismissRequest = dismissMenu,
        ) {
            menuItems(dismissMenu)
        }
    }
}
