package com.movie.scanner.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.session.ScanSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Shared bulk navigation and session-defaults prompt state for queue and capture entry.
 */
data class BulkDefaultsPromptUiState(
    val showBulkDefaultsPrompt: Boolean = false,
    val bulkDefaultsSetupPending: Boolean = false,
    val bulkDefaultsSetupDiscTypePending: Boolean = false,
    val bulkDefaultsSetupLocationPending: Boolean = false,
    val showDiscTypeDialog: Boolean = false,
    val showLocationDialog: Boolean = false,
    val bulkLocation: String = "",
)

@HiltViewModel
class ScanBulkNavigationViewModel @Inject constructor(
    bulkImageRepository: BulkImageRepository,
    private val scanSessionHolder: ScanSessionHolder,
) : ViewModel() {
    val hasUnprocessedRecords: StateFlow<Boolean> = bulkImageRepository.observeHasUnprocessedRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _bulkDefaultsPromptUiState = MutableStateFlow(buildBulkDefaultsPromptUiState())
    val bulkDefaultsPromptUiState: StateFlow<BulkDefaultsPromptUiState> =
        _bulkDefaultsPromptUiState.asStateFlow()

    /**
     * Shows the session defaults prompt when batch disc type or location is still unset.
     */
    fun offerBulkDefaultsPromptIfNeeded(skipForRescan: Boolean = false) {
        if (!shouldOfferBulkDefaultsSetup(skipForRescan)) {
            return
        }
        _bulkDefaultsPromptUiState.update { state ->
            state.copy(
                showBulkDefaultsPrompt = true,
                bulkLocation = resolveBulkLocationDraft(),
            )
        }
    }

    /**
     * Closes the bulk defaults prompt without opening disc type or location setup.
     */
    fun dismissBulkDefaultsPrompt() {
        scanSessionHolder.markBulkDefaultsPromptHandled()
        _bulkDefaultsPromptUiState.update { it.copy(showBulkDefaultsPrompt = false) }
    }

    /**
     * Accepts bulk defaults setup and walks through any unset disc type and location prompts.
     */
    fun acceptBulkDefaultsSetup() {
        val discTypeUnset = scanSessionHolder.bulkBatchDiscType.isNullOrBlank()
        val locationUnset = scanSessionHolder.bulkBatchLocation.isBlank()
        scanSessionHolder.markBulkDefaultsPromptHandled()
        _bulkDefaultsPromptUiState.update {
            it.copy(
                showBulkDefaultsPrompt = false,
                bulkDefaultsSetupPending = true,
                bulkDefaultsSetupDiscTypePending = discTypeUnset,
                bulkDefaultsSetupLocationPending = locationUnset,
                bulkLocation = resolveBulkLocationDraft(),
            )
        }
        advanceBulkDefaultsSetup()
    }

    /**
     * Opens the bulk disc type picker.
     */
    fun openDiscTypeDialog() {
        _bulkDefaultsPromptUiState.update { it.copy(showDiscTypeDialog = true) }
    }

    /**
     * Closes the disc type picker without persisting a selection.
     */
    fun dismissDiscTypeDialog() {
        _bulkDefaultsPromptUiState.update { state ->
            state.copy(
                showDiscTypeDialog = false,
                bulkDefaultsSetupDiscTypePending = if (state.bulkDefaultsSetupPending) {
                    false
                } else {
                    state.bulkDefaultsSetupDiscTypePending
                },
            )
        }
        advanceBulkDefaultsSetup()
    }

    /**
     * Saves the bulk disc type for later review forms.
     */
    fun saveBulkDiscType(discType: String?) {
        scanSessionHolder.rememberBulkBatchDiscType(discType)
        _bulkDefaultsPromptUiState.update { state ->
            state.copy(
                showDiscTypeDialog = false,
                bulkDefaultsSetupDiscTypePending = if (state.bulkDefaultsSetupPending) {
                    false
                } else {
                    state.bulkDefaultsSetupDiscTypePending
                },
            )
        }
        advanceBulkDefaultsSetup()
    }

    /**
     * Opens the bulk location prompt with the current saved location as the draft default.
     */
    fun openLocationDialog() {
        _bulkDefaultsPromptUiState.update {
            it.copy(
                showLocationDialog = true,
                bulkLocation = resolveBulkLocationDraft(),
            )
        }
    }

    /**
     * Closes the location prompt without persisting draft edits.
     */
    fun dismissLocationDialog() {
        _bulkDefaultsPromptUiState.update { state ->
            state.copy(
                showLocationDialog = false,
                bulkDefaultsSetupLocationPending = if (state.bulkDefaultsSetupPending) {
                    false
                } else {
                    state.bulkDefaultsSetupLocationPending
                },
            )
        }
        if (_bulkDefaultsPromptUiState.value.bulkDefaultsSetupPending) {
            finishBulkDefaultsSetup()
        }
    }

    /**
     * Saves the bulk location for later review forms.
     */
    fun saveBulkLocation(location: String) {
        scanSessionHolder.rememberBulkBatchLocation(location)
        _bulkDefaultsPromptUiState.update { state ->
            state.copy(
                showLocationDialog = false,
                bulkLocation = location,
                bulkDefaultsSetupLocationPending = if (state.bulkDefaultsSetupPending) {
                    false
                } else {
                    state.bulkDefaultsSetupLocationPending
                },
            )
        }
        if (_bulkDefaultsPromptUiState.value.bulkDefaultsSetupPending) {
            finishBulkDefaultsSetup()
        }
    }

    private fun buildBulkDefaultsPromptUiState(): BulkDefaultsPromptUiState =
        BulkDefaultsPromptUiState(bulkLocation = resolveBulkLocationDraft())

    private fun resolveBulkLocationDraft(): String =
        scanSessionHolder.bulkBatchLocation.ifBlank {
            scanSessionHolder.lastReviewLocation
        }

    private fun shouldOfferBulkDefaultsSetup(skipForRescan: Boolean): Boolean {
        if (skipForRescan || scanSessionHolder.bulkDefaultsPromptHandled) {
            return false
        }
        val discTypeUnset = scanSessionHolder.bulkBatchDiscType.isNullOrBlank()
        val locationUnset = scanSessionHolder.bulkBatchLocation.isBlank()
        return discTypeUnset || locationUnset
    }

    private fun advanceBulkDefaultsSetup() {
        if (!_bulkDefaultsPromptUiState.value.bulkDefaultsSetupPending) {
            return
        }
        if (_bulkDefaultsPromptUiState.value.bulkDefaultsSetupDiscTypePending) {
            if (!_bulkDefaultsPromptUiState.value.showDiscTypeDialog) {
                openDiscTypeDialog()
            }
            return
        }
        if (_bulkDefaultsPromptUiState.value.bulkDefaultsSetupLocationPending) {
            if (!_bulkDefaultsPromptUiState.value.showLocationDialog) {
                openLocationDialog()
            }
            return
        }
        finishBulkDefaultsSetup()
    }

    private fun finishBulkDefaultsSetup() {
        _bulkDefaultsPromptUiState.update {
            it.copy(
                bulkDefaultsSetupPending = false,
                bulkDefaultsSetupDiscTypePending = false,
                bulkDefaultsSetupLocationPending = false,
            )
        }
    }
}
