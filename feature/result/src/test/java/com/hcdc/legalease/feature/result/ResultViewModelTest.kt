package com.hcdc.legalease.feature.result

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.testing.sampleReport
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ResultViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val base = Analysis(
        "a1", "lease.pdf", SourceKind.PDF, AnalysisStatus.QUEUED, 0, 0, unreadablePages = listOf(2),
    )

    @Test
    fun `shows the stored report with the remote threshold text`() = runTest {
        val repository = FakeAnalysisRepository()
        val report = sampleReport(Classification.RESCISSIBLE, total = 0.85f)
        repository.put(base.copy(status = AnalysisStatus.COMPLETED, report = report))
        val savedState = SavedStateHandle(mapOf(ResultRoute.ARG_ANALYSIS_ID to "a1"))
        val vm = ResultViewModel(savedState, ObserveAnalysisUseCase(repository), FakeFeatureFlags())

        vm.uiState.test {
            val result = (expectMostRecentItem() as ResultUiState.Success).result
            assertEquals(report, result.report)
            assertEquals("lease.pdf", result.title)
            assertEquals(listOf(3), result.unreadablePages)
            assertEquals(Classification.RESCISSIBLE, result.report.shownClassification)
            assertEquals(true, result.thresholdText.contains("80%"))
        }
    }

    @Test
    fun `below 0_80 nothing is shown as a verdict`() {
        val report = sampleReport(Classification.VOID, 0.79f)
        val state = toUiState(base.copy(status = AnalysisStatus.COMPLETED, report = report))
        assertNull((state as ResultUiState.Success).result.report.shownClassification)
    }

    @Test
    fun `earlier-version results are never shown as a verdict`() {
        val legacy = sampleReport(Classification.VOID, 0.99f, AnalysisMethod.LEGACY)
        val state = toUiState(base.copy(status = AnalysisStatus.COMPLETED, report = legacy))
        assertNull((state as ResultUiState.Success).result.report.shownClassification)
    }

    @Test
    fun `state mapping covers every status`() {
        assertEquals(ResultUiState.Error(AppError.DocumentNotFound), toUiState(null))
        listOf(
            AnalysisStatus.QUEUED, AnalysisStatus.EXTRACTING_TEXT, AnalysisStatus.CLASSIFYING,
            AnalysisStatus.ANALYZING_PAGES, AnalysisStatus.CHECKING,
        ).forEach { assertEquals(it.name, ResultUiState.Loading, toUiState(base.copy(status = it))) }
        assertEquals(
            ResultUiState.Error(AppError.ModelUnavailable),
            toUiState(base.copy(status = AnalysisStatus.FAILED, error = AppError.ModelUnavailable)),
        )
        assertEquals(ResultUiState.Error(AppError.Cancelled), toUiState(base.copy(status = AnalysisStatus.CANCELLED)))
    }

    @Test
    fun `completed without a report is an error, never a fabricated result`() {
        val state = toUiState(base.copy(status = AnalysisStatus.COMPLETED, report = null))
        assertEquals(ResultUiState.Error(AppError.Unexpected("missing_error")), state)
    }
}
