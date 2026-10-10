package com.hcdc.legalease.feature.history

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.testing.sampleReport
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.usecase.DeleteAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.GetHistoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repository = FakeAnalysisRepository().apply {
        put(
            Analysis(
                "a1", "Lease of apartment", SourceKind.PDF, AnalysisStatus.COMPLETED,
                createdAtMillis = 3, updatedAtMillis = 3,
                report = sampleReport(Classification.VOIDABLE),
            )
        )
        put(Analysis("a2", "Loan agreement", SourceKind.IMAGES, AnalysisStatus.FAILED, 2, 2))
        put(Analysis("a3", "Still running", SourceKind.PDF, AnalysisStatus.EXTRACTING_TEXT, 1, 1))
    }

    private fun TestScope.viewModel() = HistoryViewModel(
        GetHistoryUseCase(repository),
        DeleteAnalysisUseCase(repository, FakeSyncScheduler(), FakeAnalytics()),
        appScope = this,
    )

    @Test
    fun `lists finished analyses newest first`() = runTest {
        assertEquals(
            listOf(HistoryItem("a1", "Lease of apartment"), HistoryItem("a2", "Loan agreement")),
            viewModel().items.asSnapshot(),
        )
    }

    @Test
    fun `search matches the name or the classification`() = runTest {
        val vm = viewModel()

        vm.onQueryChange("loan")
        assertEquals(listOf("a2"), vm.items.asSnapshot().map { it.analysisId })

        vm.onQueryChange("voidable")
        assertEquals(listOf("a1"), vm.items.asSnapshot().map { it.analysisId })
    }

    @Test
    fun `deleting hides the row at once and undo brings it back`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onDelete("a1")
            assertEquals(HistoryEvent.ShowUndo, awaitItem())
        }
        assertEquals(listOf("a2"), vm.items.asSnapshot().map { it.analysisId })

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2"), vm.items.asSnapshot().map { it.analysisId })
        assertNotNull(repository.get("a1"))
    }

    @Test
    fun `the deletion is committed when the undo window closes`() = runTest {
        val vm = viewModel()
        vm.onDelete("a1")

        vm.onUndoExpired()
        advanceUntilIdle()

        assertNull(repository.get("a1"))
    }

    @Test
    fun `deleting another row commits the previous one`() = runTest {
        val vm = viewModel()
        vm.onDelete("a1")
        vm.onDelete("a2")
        advanceUntilIdle()

        assertNull(repository.get("a1"))
        assertNotNull(repository.get("a2"))
    }
}
