package com.hcdc.legalease.feature.generator

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hcdc.legalease.core.domain.engine.ContractDocuments
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.generator.RenderedContract
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalysisScheduler
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeAuditLog
import com.hcdc.legalease.core.domain.testing.FakeContractAssistant
import com.hcdc.legalease.core.domain.testing.FakeContractRepository
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.FakeIntegrityChecker
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.usecase.AnalyzeGeneratedContractUseCase
import com.hcdc.legalease.core.domain.usecase.AssistantAvailabilityUseCase
import com.hcdc.legalease.core.domain.usecase.ExportContractUseCase
import com.hcdc.legalease.core.domain.usecase.GetContractsUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveContractVersionUseCase
import com.hcdc.legalease.core.domain.usecase.RewriteClauseUseCase
import com.hcdc.legalease.core.domain.usecase.SaveContractVersionUseCase
import com.hcdc.legalease.core.domain.usecase.SuggestClausesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class GeneratorScreensTest {

    @get:Rule val compose = createComposeRule()

    private val contracts = FakeContractRepository()

    @Test
    fun theHomeScreenOffersEveryTemplate() {
        var opened: ContractEditorRoute? = null
        val viewModel = GeneratorHomeViewModel(GetContractsUseCase(contracts))
        compose.setContent { GeneratorHomeScreen(onOpenEditor = { opened = it }, viewModel = viewModel) }
        listOf("Contract of Lease", "Contract of Loan", "Deed of Absolute Sale", "Lease of Personal Property").forEach {
            compose.onNodeWithText(it, substring = true).performScrollTo().assertExists()
        }
        compose.onNodeWithText("Contract of Loan", substring = true).performClick()
        assertEquals("LOAN", opened?.type)
    }

    @Test
    fun theFormDoesNotAdvancePastMissingFields() {
        val viewModel = editor()
        compose.setContent { ContractEditorScreen(onOpenAnalysis = {}, onBack = {}, viewModel = viewModel) }
        compose.onNodeWithText("Next").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Required.").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun editor(): ContractEditorViewModel {
        val analyses = FakeAnalysisRepository()
        val assistant = FakeContractAssistant()
        val documents = NoDocuments()
        val clock = Clock.systemUTC()
        return ContractEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ContractEditorRoute.ARG_TYPE to "LEASE")),
            saveVersion = SaveContractVersionUseCase(contracts, FakeSyncScheduler(), FakeAnalytics(), FakeAuditLog(), clock),
            observeVersion = ObserveContractVersionUseCase(contracts),
            observeAnalysis = ObserveAnalysisUseCase(analyses),
            analyze = AnalyzeGeneratedContractUseCase(analyses, contracts, FakeAnalysisScheduler(), FakeAnalytics()),
            assistantAvailability = AssistantAvailabilityUseCase(assistant, FakeFeatureFlags(), FakeIntegrityChecker()),
            suggestClauses = SuggestClausesUseCase(assistant, FakeAnalytics()),
            rewriteClause = RewriteClauseUseCase(assistant, FakeAnalytics()),
            assistant = assistant,
            documents = documents,
            exportContract = ExportContractUseCase(documents, FakeAnalytics(), FakeAuditLog(), clock),
            analytics = FakeAnalytics(),
            clock = clock,
            appScope = CoroutineScope(SupervisorJob()),
        )
    }

    private class NoDocuments : ContractDocuments {
        override suspend fun pageTexts(contract: RenderedContract, size: PaperSize) = listOf(contract.title)
        override suspend fun export(contract: RenderedContract, size: PaperSize, fileName: String) = File(fileName)
        override suspend fun write(contract: RenderedContract, size: PaperSize, target: File) = Unit
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
