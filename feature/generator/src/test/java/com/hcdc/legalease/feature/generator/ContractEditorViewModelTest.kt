package com.hcdc.legalease.feature.generator

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import com.hcdc.legalease.core.domain.generator.FieldError
import com.hcdc.legalease.core.domain.generator.FormField
import com.hcdc.legalease.core.domain.generator.RenderedContract
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.CivilStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.Party
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.engine.ContractDocuments
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAuditLog
import com.hcdc.legalease.core.domain.testing.FakeIntegrityChecker
import com.hcdc.legalease.core.domain.usecase.ExportContractUseCase
import com.hcdc.legalease.core.domain.testing.FakeAnalysisScheduler
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeContractAssistant
import com.hcdc.legalease.core.domain.testing.FakeContractRepository
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.testing.sampleReport
import com.hcdc.legalease.core.domain.usecase.AnalyzeGeneratedContractUseCase
import com.hcdc.legalease.core.domain.usecase.AssistantAvailabilityUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveContractVersionUseCase
import com.hcdc.legalease.core.domain.usecase.RewriteClauseUseCase
import com.hcdc.legalease.core.domain.usecase.SaveContractVersionUseCase
import com.hcdc.legalease.core.domain.usecase.SuggestClausesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ContractEditorViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val contracts = FakeContractRepository()
    private val analyses = FakeAnalysisRepository()
    private val scheduler = FakeAnalysisScheduler()
    private val assistant = FakeContractAssistant()
    private val documents = FakeDocuments()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.viewModel(args: Map<String, Any?> = mapOf(ContractEditorRoute.ARG_TYPE to "LEASE")) =
        ContractEditorViewModel(
            savedStateHandle = SavedStateHandle(args),
            saveVersion = SaveContractVersionUseCase(
                contracts, FakeSyncScheduler(), FakeAnalytics(), FakeAuditLog(), clock,
            ),
            observeVersion = ObserveContractVersionUseCase(contracts),
            observeAnalysis = ObserveAnalysisUseCase(analyses),
            analyze = AnalyzeGeneratedContractUseCase(analyses, contracts, scheduler, FakeAnalytics()),
            assistantAvailability = AssistantAvailabilityUseCase(assistant, FakeFeatureFlags(), FakeIntegrityChecker()),
            suggestClauses = SuggestClausesUseCase(assistant, FakeAnalytics()),
            rewriteClause = RewriteClauseUseCase(assistant, FakeAnalytics()),
            assistant = assistant,
            documents = documents,
            exportContract = ExportContractUseCase(documents, FakeAnalytics(), FakeAuditLog(), clock),
            analytics = FakeAnalytics(),
            clock = clock,
            appScope = backgroundScope,
        )

    @Test
    fun `a new contract starts on the first step with today's date for signing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(EditorStage.FORM, state.stage)
        assertEquals(ContractType.LEASE, state.draft.type)
        assertEquals("2026-10-15", state.draft.executionDate)
    }

    @Test
    fun `next shows errors for the current step and does not advance`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.next()
        assertEquals(0, vm.uiState.value.stepIndex)
        assertEquals(FieldError.REQUIRED, vm.uiState.value.errors[FormField.FIRST_NAME])
        // Errors update live once shown.
        vm.update { it.copy(firstParty = it.firstParty.copy(name = "Juan")) }
        assertNull(vm.uiState.value.errors[FormField.FIRST_NAME])
    }

    @Test
    fun `a complete form reaches the preview`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.update { filled(ContractType.LEASE) }
        repeat(vm.uiState.value.steps.size) { vm.next() }
        assertEquals(EditorStage.PREVIEW, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `save and analyze stores version 1, analyses the exported pages, and frees the model first`() = runTest {
        val vm = previewOf(ContractType.LOAN)
        vm.saveAndAnalyze()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.saved!!.version)
        assertEquals(1, assistant.released)
        val analysisId = scheduler.enqueued.single()
        assertEquals(documents.pages, analyses.texts.getValue(analysisId).pages)
        assertFalse("export waits for the analysis", state.canExport)
    }

    @Test
    fun `export is offered once the analysis finishes, and shares the file`() = runTest {
        val vm = previewOf(ContractType.SALE)
        vm.saveAndAnalyze()
        advanceUntilIdle()
        val analysisId = scheduler.enqueued.single()
        analyses.complete(analysisId, sampleReport(Classification.ENFORCEABLE))
        advanceUntilIdle()
        assertEquals(AnalysisStatus.COMPLETED, vm.uiState.value.analysis?.status)
        assertTrue(vm.uiState.value.canExport)

        vm.events.test {
            vm.setPaper(PaperSize.LEGAL)
            vm.export()
            val share = awaitItem() as EditorEvent.Share
            assertEquals("Deed of Absolute Sale v1 (LEGAL).pdf", share.file.name)
            assertEquals(PaperSize.LEGAL, documents.exportedSize)
        }
    }

    @Test
    fun `editing a saved version makes a new version and drops the old analysis`() = runTest {
        val vm = previewOf(ContractType.LEASE)
        vm.saveAndAnalyze()
        advanceUntilIdle()
        val first = vm.uiState.value.saved!!

        vm.edit()
        vm.update { it.copy(amount = "20,000") }
        assertNull(vm.uiState.value.saved)
        assertNull(vm.uiState.value.analysis)
        repeat(vm.uiState.value.steps.size) { vm.next() }
        vm.saveAndAnalyze()
        advanceUntilIdle()

        val second = vm.uiState.value.saved!!
        assertEquals(first.contractId, second.contractId)
        assertEquals(2, second.version)
        assertEquals(2, contracts.versions.value.size)
    }

    @Test
    fun `assistant suggestions change nothing until accepted`() = runTest {
        assistant.suggestions = AppResult.Success(
            listOf(
                ClauseSuggestion("Parking", "The LESSEE may use one parking slot."),
                ClauseSuggestion("Keys", "The LESSOR shall give the LESSEE two sets of keys."),
            )
        )
        val vm = previewOf(ContractType.LEASE)
        vm.suggest()
        advanceUntilIdle()
        assertEquals("Parking", vm.uiState.value.proposal!!.clause.title)
        assertTrue(vm.uiState.value.draft.optionalClauses.isEmpty())

        vm.acceptProposal()
        assertEquals(listOf("Parking"), vm.uiState.value.draft.optionalClauses.map { it.title })
        assertEquals("Keys", vm.uiState.value.proposal!!.clause.title)

        vm.nextProposal()
        assertNull(vm.uiState.value.proposal)
        assertEquals(listOf("Parking"), vm.uiState.value.draft.optionalClauses.map { it.title })
    }

    @Test
    fun `withheld suggestions are reported, not shown`() = runTest {
        assistant.suggestions = AppResult.Success(listOf(ClauseSuggestion("Law", "As provided by law, rent is due.")))
        val vm = previewOf(ContractType.LEASE)
        vm.suggest()
        advanceUntilIdle()
        assertNull(vm.uiState.value.proposal)
        assertEquals(EditorMessage.SuggestionsWithheld(1), vm.uiState.value.message)
    }

    @Test
    fun `opening a saved version shows its preview`() = runTest {
        val saved = contracts.saveVersion(null, filled(ContractType.LEASE), "CONTRACT OF LEASE", "body")
        val vm = viewModel(mapOf(ContractEditorRoute.ARG_VERSION_ID to saved.id))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(EditorStage.PREVIEW, state.stage)
        assertEquals(saved.id, state.saved?.id)
        assertFalse(state.hasUnsavedChanges)
    }

    private fun TestScope.previewOf(type: ContractType): ContractEditorViewModel {
        val vm = viewModel(mapOf(ContractEditorRoute.ARG_TYPE to type.name))
        advanceUntilIdle()
        vm.update { filled(type) }
        repeat(vm.uiState.value.steps.size) { vm.next() }
        check(vm.uiState.value.stage == EditorStage.PREVIEW) { vm.uiState.value.errors.toString() }
        return vm
    }

    private fun filled(type: ContractType) = ContractDraft(
        type = type,
        firstParty = Party("Juan dela Cruz", CivilStatus.MARRIED, "Filipino", "Davao City", "Passport", ""),
        secondParty = Party("Maria Santos", CivilStatus.SINGLE, "Filipino", "Davao City", "UMID", ""),
        partiesOfLegalAge = true,
        propertyDescription = "A house and lot in Davao City",
        amount = "15,000",
        interestRate = "0",
        startDate = "2026-11-01",
        endDate = "2027-10-31",
        placeOfExecution = "Davao City",
        executionDate = "2026-10-15",
        witnessOne = "Pedro Reyes",
        witnessTwo = "Ana Lim",
    )

    private class FakeDocuments : ContractDocuments {
        val pages = listOf("page one text", "page two text")
        var exportedSize: PaperSize? = null

        override suspend fun pageTexts(contract: RenderedContract, size: PaperSize) = pages

        override suspend fun export(contract: RenderedContract, size: PaperSize, fileName: String): File {
            exportedSize = size
            return File(fileName)
        }

        override suspend fun write(contract: RenderedContract, size: PaperSize, target: File) = Unit
    }
}
