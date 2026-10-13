package com.hcdc.legalease.feature.history

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.sampleReport
import com.hcdc.legalease.core.domain.usecase.DeleteAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.GetHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule val compose = createComposeRule()

    private val repository = FakeAnalysisRepository().apply {
        put(analysis("a1", "Lease of house.pdf", createdAt = 2))
        put(analysis("a2", "Car loan.pdf", createdAt = 1))
    }

    private fun analysis(id: String, name: String, createdAt: Long) = Analysis(
        id, name, SourceKind.PDF, AnalysisStatus.COMPLETED, createdAt, createdAt,
        report = sampleReport(Classification.ENFORCEABLE),
    )

    private fun showHistory() {
        val viewModel = HistoryViewModel(
            GetHistoryUseCase(repository),
            DeleteAnalysisUseCase(repository, FakeSyncScheduler(), FakeAnalytics()),
            appScope = CoroutineScope(SupervisorJob()),
        )
        compose.setContent { HistoryScreen(onOpenAnalysis = {}, viewModel = viewModel) }
    }

    @Test
    fun finishedScansAreListed() {
        showHistory()
        compose.onNodeWithText("Lease of house.pdf").assertExists()
        compose.onNodeWithText("Car loan.pdf").assertExists()
    }

    @Test
    fun searchingNarrowsTheList() {
        showHistory()
        compose.onNode(hasSetTextAction()).performTextInput("loan")
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Lease of house.pdf").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Car loan.pdf").assertExists()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
