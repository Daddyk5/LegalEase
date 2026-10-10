package com.hcdc.legalease.feature.history

import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.usecase.GetHistoryUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAnalysisRepository().apply {
        put(Analysis("a1", "Lease of apartment", SourceKind.PDF, AnalysisStatus.COMPLETED, 0, 0))
        put(Analysis("a2", "Loan agreement", SourceKind.IMAGES, AnalysisStatus.FAILED, 0, 0))
        put(Analysis("a3", "Still running", SourceKind.PDF, AnalysisStatus.EXTRACTING_TEXT, 0, 0))
    }

    @Test
    fun `lists finished analyses and filters by the query`() = runTest {
        val vm = HistoryViewModel(GetHistoryUseCase(repository))

        vm.uiState.test {
            assertEquals(setOf("a1", "a2"), expectMostRecentItem().items.map { it.analysisId }.toSet())

            vm.onQueryChange("loan")
            val filtered = expectMostRecentItem()
            assertEquals("loan", filtered.query)
            assertEquals(listOf(HistoryItem("a2", "Loan agreement")), filtered.items)
        }
    }
}
