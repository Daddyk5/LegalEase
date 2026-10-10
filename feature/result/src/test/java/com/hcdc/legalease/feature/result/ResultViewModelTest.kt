package com.hcdc.legalease.feature.result

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.Verdict
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ResultViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val base = Analysis("a1", "lease.pdf", SourceKind.PDF, AnalysisStatus.QUEUED, 0, 0)

    @Test
    fun `shows the stored verdict unchanged`() = runTest {
        val repository = FakeAnalysisRepository()
        val verdict = Verdict(Classification.RESCISSIBLE, 0.61f, "s")
        repository.put(base.copy(status = AnalysisStatus.COMPLETED, verdict = verdict))
        val savedState = SavedStateHandle(mapOf(ResultRoute.ARG_ANALYSIS_ID to "a1"))
        val vm = ResultViewModel(savedState, ObserveAnalysisUseCase(repository))

        vm.uiState.test {
            assertEquals(
                ResultUiState.Success(ResultModel("lease.pdf", "s", Classification.RESCISSIBLE, 0.61f)),
                expectMostRecentItem(),
            )
        }
    }

    @Test
    fun `state mapping covers every status`() {
        assertEquals(ResultUiState.Error(AppError.DocumentNotFound), toUiState(null))
        assertEquals(ResultUiState.Loading, toUiState(base))
        assertEquals(ResultUiState.Loading, toUiState(base.copy(status = AnalysisStatus.CLASSIFYING)))
        assertEquals(
            ResultUiState.Error(AppError.ModelUnavailable),
            toUiState(base.copy(status = AnalysisStatus.FAILED, error = AppError.ModelUnavailable)),
        )
        assertEquals(ResultUiState.Error(AppError.Cancelled), toUiState(base.copy(status = AnalysisStatus.CANCELLED)))
    }

    @Test
    fun `completed without a verdict is an error, never a fabricated result`() {
        val state = toUiState(base.copy(status = AnalysisStatus.COMPLETED, verdict = null))
        assertEquals(ResultUiState.Error(AppError.Unexpected("missing_error")), state)
    }
}
