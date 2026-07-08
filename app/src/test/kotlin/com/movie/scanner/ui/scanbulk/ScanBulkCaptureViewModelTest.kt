package com.movie.scanner.ui.scanbulk

import android.graphics.Bitmap
import com.movie.scanner.data.local.ApiKeyStore
import com.movie.scanner.data.model.BulkUnprocessedImageEntity
import com.movie.scanner.data.model.ScanCaptureMode
import com.movie.scanner.data.repository.BulkImageRepository
import com.movie.scanner.data.session.ScanSessionHolder
import com.movie.scanner.util.BarcodeDecoder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ScanBulkCaptureViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val apiKeyStore = mockk<ApiKeyStore>(relaxed = true)
    private val bulkImageRepository = mockk<BulkImageRepository>(relaxed = true)
    private val scanSessionHolder = mockk<ScanSessionHolder>(relaxed = true)
    private val barcodeBitmap = mockk<Bitmap>(relaxed = true)
    private val coverBitmap = mockk<Bitmap>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { apiKeyStore.hasMinimumConfiguration() } returns true
        every { scanSessionHolder.lastReviewLocation } returns ""
        every { scanSessionHolder.lastReviewDiscType } returns null
        every { scanSessionHolder.bulkBatchDiscType } returns null
        every { scanSessionHolder.resolveBulkRescanRecordId() } returns null
        every { barcodeBitmap.isRecycled } returns false
        every { coverBitmap.isRecycled } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(BarcodeDecoder)
    }

    @Test
    fun processCapturedImage_cover_returnsToBarcodeBeforeSaveCompletes() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
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
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
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

    @Test
    fun saveBulkLocation_persistsLocationAndClosesDialog() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.openLocationDialog()
        assertTrue(viewModel.uiState.value.showLocationDialog)

        viewModel.saveBulkLocation("Shelf A")
        advanceUntilIdle()

        assertEquals("Shelf A", viewModel.uiState.value.bulkLocation)
        assertEquals("Shelf A", viewModel.uiState.value.bulkBatchLocation)
        assertFalse(viewModel.uiState.value.showLocationDialog)
        verify { scanSessionHolder.rememberBulkBatchLocation("Shelf A") }
    }

    @Test
    fun saveBulkDiscType_persistsDiscTypeAndClosesDialog() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.openDiscTypeDialog()
        assertTrue(viewModel.uiState.value.showDiscTypeDialog)

        viewModel.saveBulkDiscType("Blu-Ray")
        advanceUntilIdle()

        assertEquals("Blu-Ray", viewModel.uiState.value.bulkDiscType)
        assertFalse(viewModel.uiState.value.showDiscTypeDialog)
        verify { scanSessionHolder.rememberBulkBatchDiscType("Blu-Ray") }
    }

    @Test
    fun dismissDiscTypeDialog_closesWithoutSaving() = runTest {
        every { scanSessionHolder.lastReviewDiscType } returns "DVD"
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.openDiscTypeDialog()
        viewModel.dismissDiscTypeDialog()

        assertFalse(viewModel.uiState.value.showDiscTypeDialog)
        assertEquals("DVD", viewModel.uiState.value.bulkDiscType)
        verify(exactly = 0) { scanSessionHolder.rememberBulkBatchDiscType(any()) }
    }

    @Test
    fun prepareScreen_rescanMode_enablesRescanCaptureLabels() = runTest {
        every { scanSessionHolder.resolveBulkRescanRecordId() } returns 42L

        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.prepareScreen()

        assertTrue(viewModel.uiState.value.isRescanMode)
        assertEquals("Rescan the barcode", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun processCapturedImage_rescanMode_replacesRecordAndNavigatesToLoading() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(BarcodeDecoder)
        every { BarcodeDecoder.buildBarcodeScanner() } returns mockk(relaxed = true)
        every { BarcodeDecoder.decodeFromBitmap(any(), any()) } returns "9781234567890"
        every { scanSessionHolder.resolveBulkRescanRecordId() } returns 42L
        coEvery {
            bulkImageRepository.replaceCapturedPair(
                recordId = 42L,
                barcodeBitmap = barcodeBitmap,
                coverBitmap = coverBitmap,
            )
        } returns BulkUnprocessedImageEntity(
            id = 42L,
            createdAtTimestamp = 1L,
            barcodeRelFilepath = "barcode_new.jpg",
            coverRelFilepath = "cover_new.jpg",
        )
        coEvery { bulkImageRepository.loadBitmap("barcode_new.jpg") } returns barcodeBitmap
        coEvery { bulkImageRepository.loadBitmap("cover_new.jpg") } returns coverBitmap

        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        val navigationEvents = mutableListOf<ScanBulkCaptureEvent>()
        val collectorJob = launch {
            viewModel.navigationEventFlow.collect { event ->
                navigationEvents.add(event)
            }
        }
        viewModel.prepareScreen()
        viewModel.processCapturedImage(barcodeBitmap)
        viewModel.processCapturedImage(coverBitmap)
        while (navigationEvents.isEmpty()) {
            delay(10)
        }

        assertEquals(listOf(ScanBulkCaptureEvent.NavigateToLoading), navigationEvents)
        verify { scanSessionHolder.clearBulkRescan() }
        verify {
            scanSessionHolder.startBulkItem(
                recordId = 42L,
                coverRelFilepath = "cover_new.jpg",
            )
        }
        collectorJob.cancel()
    }

    @Test
    fun onCaptureScreenResumed_clearsStaleCapturingOverlay() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.beginCaptureProcessing()
        viewModel.onCaptureScreenResumed()

        assertFalse(viewModel.uiState.value.isCapturing)
        assertFalse(viewModel.uiState.value.isProcessingCapture)
    }

    @Test
    fun onCaptureScreenResumed_clearsStaleProcessingOverlay() = runTest {
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.beginCaptureProcessing()
        viewModel.onCaptureScreenResumed()

        assertFalse(viewModel.uiState.value.isProcessingCapture)
    }

    @Test
    fun init_prefillsLocationDraftWithoutMarkingBatchLocationSet() = runTest {
        every { scanSessionHolder.lastReviewLocation } returns "Shelf A"
        every { scanSessionHolder.bulkBatchLocation } returns ""

        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)

        assertEquals("Shelf A", viewModel.uiState.value.bulkLocation)
        assertEquals("", viewModel.uiState.value.bulkBatchLocation)
    }

    @Test
    fun dismissLocationDialog_closesWithoutSaving() = runTest {
        every { scanSessionHolder.lastReviewLocation } returns "Shelf A"
        val viewModel = ScanBulkCaptureViewModel(apiKeyStore, bulkImageRepository, scanSessionHolder)
        viewModel.openLocationDialog()
        viewModel.dismissLocationDialog()

        assertFalse(viewModel.uiState.value.showLocationDialog)
        assertEquals("Shelf A", viewModel.uiState.value.bulkLocation)
        assertEquals("", viewModel.uiState.value.bulkBatchLocation)
        verify(exactly = 0) { scanSessionHolder.rememberReviewLocation(any()) }
    }
}
