package com.hcdc.legalease.feature.result

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.sampleReport
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultScreenTest {

    @get:Rule val compose = createComposeRule()

    private val repository = FakeAnalysisRepository()

    private fun show(report: AnalysisReport) {
        repository.put(
            Analysis("a1", "lease.pdf", SourceKind.PDF, AnalysisStatus.COMPLETED, 0, 0, pageCount = 1, report = report)
        )
        val viewModel = ResultViewModel(
            SavedStateHandle(mapOf(ResultRoute.ARG_ANALYSIS_ID to "a1")),
            ObserveAnalysisUseCase(repository),
            FakeFeatureFlags(),
        )
        compose.setContent { ResultScreen(onOpenPage = { _, _ -> }, viewModel = viewModel) }
    }

    @Test
    fun aConfidentResultShowsItsClassification() {
        show(sampleReport(Classification.ENFORCEABLE, total = 0.9f))
        compose.onNodeWithText("Enforceable", substring = true).assertExists()
    }

    @Test
    fun aLowConfidenceResultIsShownAsInconclusive() {
        show(sampleReport(Classification.VOID, total = 0.5f))
        compose.onNodeWithText("Inconclusive — needs review").assertExists()
        compose.onNodeWithText("Void", substring = true).assertDoesNotExist()
    }
}
