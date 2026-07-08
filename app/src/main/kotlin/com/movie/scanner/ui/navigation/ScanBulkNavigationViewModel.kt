package com.movie.scanner.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movie.scanner.data.repository.BulkImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes bulk-queue navigation state for the bottom bar Scan Bulk tab.
 */
@HiltViewModel
class ScanBulkNavigationViewModel @Inject constructor(
    bulkImageRepository: BulkImageRepository,
) : ViewModel() {
    val hasUnprocessedRecords: StateFlow<Boolean> = bulkImageRepository.observeHasUnprocessedRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}
