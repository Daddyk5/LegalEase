package com.hcdc.legalease.feature.scan

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalysisScheduler
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.usecase.CancelAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.StartAnalysisUseCase
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.Verdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ScanViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAnalysisRepository()
    private val scheduler = FakeAnalysisScheduler()
    private val savedState = SavedStateHandle()

    private fun viewModel() = ScanViewModel(
        savedState,
        StartAnalysisUseCase(repository, scheduler, FakeAnalytics()),
        ObserveAnalysisUseCase(repository),
        CancelAnalysisUseCase(repository, scheduler),
    )

    @Test
    fun `scanning a pdf schedules it and reports page progress`() = runTest {
        val vm = viewModel()
        vm.onPdfSelected("content://lease.pdf", "lease.pdf")

        vm.scanPdf(fallbackName = "Uploaded PDF")
        val id = scheduler.enqueued.single()
        assertEquals("lease.pdf", repository.get(id)?.documentName)
        assertEquals(ScanProgress.Preparing, vm.uiState.value.progress)

        repository.markExtracting(id, currentPage = 2, pageCount = 3)
        assertEquals(ScanProgress.ReadingPage(2, 3), vm.uiState.value.progress)
    }

    @Test
    fun `completion navigates to the result and clears progress`() = runTest {
        val vm = viewModel()
        vm.onImagesSelected(listOf("content://1", "content://2"))
        vm.scanImages("Scanned images")
        val id = scheduler.enqueued.single()

        vm.events.test {
            repository.complete(id, Verdict(Classification.VOID, 0.9f, "s"))
            assertEquals(ScanEvent.NavigateToResult(id), awaitItem())
        }
        assertNull(vm.uiState.value.progress)
        assertEquals(SourceKind.IMAGES, repository.get(id)?.sourceKind)
    }

    @Test
    fun `upload problems show an error on the scan screen`() = runTest {
        val vm = viewModel()
        vm.onPdfSelected("content://x.pdf", "x.pdf")
        vm.scanPdf("Uploaded PDF")
        val id = scheduler.enqueued.single()

        vm.events.test {
            repository.fail(id, AppError.NoTextFound)
            assertEquals(ScanEvent.ShowError(AppError.NoTextFound), awaitItem())
        }
    }

    @Test
    fun `model problems are explained on the result screen`() = runTest {
        val vm = viewModel()
        vm.onPdfSelected("content://x.pdf", "x.pdf")
        vm.scanPdf("Uploaded PDF")
        val id = scheduler.enqueued.single()

        vm.events.test {
            repository.fail(id, AppError.ModelUnavailable)
            assertEquals(ScanEvent.NavigateToResult(id), awaitItem())
        }
    }

    @Test
    fun `a failed copy shows an error and schedules nothing`() = runTest {
        repository.createResult = AppResult.Failure(AppError.StorageFull)
        val vm = viewModel()
        vm.onPdfSelected("content://x.pdf", "x.pdf")

        vm.events.test {
            vm.scanPdf("Uploaded PDF")
            assertEquals(ScanEvent.ShowError(AppError.StorageFull), awaitItem())
        }
        assertEquals(emptyList<String>(), scheduler.enqueued)
        assertNull(vm.uiState.value.progress)
    }

    @Test
    fun `cancel stops the background work`() = runTest {
        val vm = viewModel()
        vm.onPdfSelected("content://x.pdf", "x.pdf")
        vm.scanPdf("Uploaded PDF")
        val id = scheduler.enqueued.single()

        vm.cancelScan()

        assertEquals(listOf(id), scheduler.cancelled)
        assertNull(vm.uiState.value.progress)
    }

    @Test
    fun `a recreated view model resumes following the running scan`() = runTest {
        viewModel().apply {
            onPdfSelected("content://x.pdf", "x.pdf")
            scanPdf("Uploaded PDF")
        }
        val id = scheduler.enqueued.single()
        repository.markExtracting(id, currentPage = 1, pageCount = 4)

        val recreated = viewModel()

        assertEquals(ScanProgress.ReadingPage(1, 4), recreated.uiState.value.progress)
    }
}
