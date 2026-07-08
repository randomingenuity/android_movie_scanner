package com.movie.scanner.ui.navigation

import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.session.ScanSessionHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanBulkNavigationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { bulkImageRepository.observeHasUnprocessedRecords() } returns flowOf(false)
        every { scanSessionHolder.lastReviewLocation } returns ""
        every { scanSessionHolder.bulkBatchDiscType } returns null
        every { scanSessionHolder.bulkBatchLocation } returns ""
        every { scanSessionHolder.bulkDefaultsPromptHandled } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun offerBulkDefaultsPromptIfNeeded_showsPromptWhenBatchDefaultsAreUnset() = runTest {
        val viewModel = ScanBulkNavigationViewModel(
            bulkImageRepository = bulkImageRepository,
            scanSessionHolder = scanSessionHolder,
        )
        advanceUntilIdle()

        viewModel.offerBulkDefaultsPromptIfNeeded()

        assertTrue(viewModel.bulkDefaultsPromptUiState.value.showBulkDefaultsPrompt)
    }

    @Test
    fun offerBulkDefaultsPromptIfNeeded_skipsPromptWhenBatchDefaultsAreSet() = runTest {
        every { scanSessionHolder.bulkBatchDiscType } returns "Blu-Ray"
        every { scanSessionHolder.bulkBatchLocation } returns "Shelf A"

        val viewModel = ScanBulkNavigationViewModel(
            bulkImageRepository = bulkImageRepository,
            scanSessionHolder = scanSessionHolder,
        )
        advanceUntilIdle()

        viewModel.offerBulkDefaultsPromptIfNeeded()

        assertFalse(viewModel.bulkDefaultsPromptUiState.value.showBulkDefaultsPrompt)
    }

    @Test
    fun dismissBulkDefaultsPrompt_marksPromptHandledAndClosesDialog() = runTest {
        val viewModel = ScanBulkNavigationViewModel(
            bulkImageRepository = bulkImageRepository,
            scanSessionHolder = scanSessionHolder,
        )
        advanceUntilIdle()

        viewModel.offerBulkDefaultsPromptIfNeeded()
        viewModel.dismissBulkDefaultsPrompt()

        assertFalse(viewModel.bulkDefaultsPromptUiState.value.showBulkDefaultsPrompt)
        verify { scanSessionHolder.markBulkDefaultsPromptHandled() }
    }

    @Test
    fun acceptBulkDefaultsSetup_opensDiscTypeThenLocationPrompts() = runTest {
        val viewModel = ScanBulkNavigationViewModel(
            bulkImageRepository = bulkImageRepository,
            scanSessionHolder = scanSessionHolder,
        )
        advanceUntilIdle()

        viewModel.offerBulkDefaultsPromptIfNeeded()
        viewModel.acceptBulkDefaultsSetup()

        assertFalse(viewModel.bulkDefaultsPromptUiState.value.showBulkDefaultsPrompt)
        assertTrue(viewModel.bulkDefaultsPromptUiState.value.showDiscTypeDialog)

        viewModel.dismissDiscTypeDialog()

        assertTrue(viewModel.bulkDefaultsPromptUiState.value.showLocationDialog)

        viewModel.dismissLocationDialog()

        assertFalse(viewModel.bulkDefaultsPromptUiState.value.bulkDefaultsSetupPending)
        verify { scanSessionHolder.markBulkDefaultsPromptHandled() }
    }

    @Test
    fun saveBulkLocation_persistsLocationAndClosesDialog() = runTest {
        val viewModel = ScanBulkNavigationViewModel(
            bulkImageRepository = bulkImageRepository,
            scanSessionHolder = scanSessionHolder,
        )
        advanceUntilIdle()

        viewModel.openLocationDialog()
        assertTrue(viewModel.bulkDefaultsPromptUiState.value.showLocationDialog)

        viewModel.saveBulkLocation("Shelf A")

        assertEquals("Shelf A", viewModel.bulkDefaultsPromptUiState.value.bulkLocation)
        assertFalse(viewModel.bulkDefaultsPromptUiState.value.showLocationDialog)
        verify { scanSessionHolder.rememberBulkBatchLocation("Shelf A") }
    }

    @Test
    fun saveBulkDiscType_persistsDiscTypeAndClosesDialog() = runTest {
        val viewModel = ScanBulkNavigationViewModel(
            bulkImageRepository = bulkImageRepository,
            scanSessionHolder = scanSessionHolder,
        )
        advanceUntilIdle()

        viewModel.openDiscTypeDialog()
        assertTrue(viewModel.bulkDefaultsPromptUiState.value.showDiscTypeDialog)

        viewModel.saveBulkDiscType("Blu-Ray")

        assertFalse(viewModel.bulkDefaultsPromptUiState.value.showDiscTypeDialog)
        verify { scanSessionHolder.rememberBulkBatchDiscType("Blu-Ray") }
    }
}
