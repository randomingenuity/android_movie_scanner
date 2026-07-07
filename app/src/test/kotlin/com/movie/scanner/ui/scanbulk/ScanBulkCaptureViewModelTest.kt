package com.movie.scanner.ui.scanbulk

import android.graphics.Bitmap
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.data.repository.BulkImageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanBulkCaptureViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val apiKeyStore = mockk<ApiKeyStore>(relaxed = true)
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val barcodeBitmap = mockk<Bitmap>(relaxed = true)
    private val coverBitmap = mockk<Bitmap>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { apiKeyStore.hasMinimumConfiguration() } returns true
        every { barcodeBitmap.isRecycled } returns false
        every { coverBitmap.isRecycled } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun processCapturedImage_cover_returnsToBarcodeBeforeSaveCompletes() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository)
        viewModel.prepareScreen()
        viewModel.processCapturedImage(barcodeBitmap)
        advanceUntilIdle()

        viewModel.processCapturedImage(coverBitmap)
        advanceUntilIdle()

        assertEquals(ScanCaptureMode.BARCODE, viewModel.uiState.value.captureMode)
        assertEquals(1, viewModel.uiState.value.pairCount)
        assertFalse(viewModel.uiState.value.isProcessingCapture)
        verify {
            bulkImageRepository.enqueueCapturedPair(
                barcodeBitmap = barcodeBitmap,
                coverBitmap = coverBitmap,
                onFailure = any(),
            )
        }
    }

    @Test
    fun processCapturedImage_cover_surfacesEnqueueFailure() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository)
        viewModel.prepareScreen()
        viewModel.processCapturedImage(barcodeBitmap)
        advanceUntilIdle()

        val failureHandler = slot<(String) -> Unit>()
        every {
            bulkImageRepository.enqueueCapturedPair(
                barcodeBitmap = any(),
                coverBitmap = any(),
                onFailure = capture(failureHandler),
            )
        } answers { }

        viewModel.processCapturedImage(coverBitmap)
        advanceUntilIdle()
        failureHandler.captured.invoke("Disk full")
        advanceUntilIdle()

        assertEquals("Disk full", viewModel.uiState.value.captureErrorMessage)
    }
}
