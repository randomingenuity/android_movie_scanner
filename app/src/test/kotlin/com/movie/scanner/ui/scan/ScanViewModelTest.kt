package com.movie.scanner.ui.scan

import android.graphics.Bitmap
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.BarcodeDecoder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
class ScanViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val apiKeyStore = mockk<ApiKeyStore>(relaxed = true)
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)
    private val coverBitmap = mockk<Bitmap>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(BarcodeDecoder)
        every { BarcodeDecoder.buildBarcodeScanner() } returns mockk(relaxed = true)
        every { apiKeyStore.hasMinimumConfiguration() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(BarcodeDecoder)
    }

    @Test
    fun processCapturedImage_cover_clearsProcessingBeforeNavigation() = runTest {
        val viewModel = ScanViewModel(apiKeyStore, scanSessionHolder)
        viewModel.beginCaptureProcessing()
        viewModel.skipBarcode()
        advanceUntilIdle()

        viewModel.processCapturedImage(coverBitmap)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isProcessingCapture)
        verify { scanSessionHolder.recordCoverCapture(coverBitmap) }
    }

    @Test
    fun onCaptureScreenResumed_coverRetakeRequest_resumesCoverCaptureAndClearsProcessing() = runTest {
        every { scanSessionHolder.consumeCoverRetakeRequest() } returns true

        val viewModel = ScanViewModel(apiKeyStore, scanSessionHolder)
        viewModel.beginCaptureProcessing()
        viewModel.onCaptureScreenResumed()
        advanceUntilIdle()

        assertEquals(ScanCaptureMode.COVER, viewModel.uiState.value.captureMode)
        assertFalse(viewModel.uiState.value.isProcessingCapture)
    }

    @Test
    fun onCaptureScreenResumed_staleProcessingOverlay_clearsProcessing() = runTest {
        every { scanSessionHolder.consumeCoverRetakeRequest() } returns false

        val viewModel = ScanViewModel(apiKeyStore, scanSessionHolder)
        viewModel.beginCaptureProcessing()
        viewModel.onCaptureScreenResumed()

        assertFalse(viewModel.uiState.value.isProcessingCapture)
    }
}
