package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.generator.ClauseRejection
import com.hcdc.legalease.core.domain.generator.DiffSegment
import com.hcdc.legalease.core.domain.generator.validDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.repository.FeatureFlags
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalysisScheduler
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeContractAssistant
import com.hcdc.legalease.core.domain.testing.FakeContractRepository
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.Instant
import java.time.Clock
import com.hcdc.legalease.core.domain.testing.FakeIntegrityChecker
import com.hcdc.legalease.core.domain.testing.FakeAuditLog
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.model.AuditEvent

class ContractUseCasesTest {

    private val contracts = FakeContractRepository()
    private val analyses = FakeAnalysisRepository()
    private val scheduler = FakeAnalysisScheduler()
    private val assistant = FakeContractAssistant()
    private val auditLog = FakeAuditLog()
    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val save = SaveContractVersionUseCase(contracts, FakeSyncScheduler(), FakeAnalytics(), auditLog, clock)

    @Test
    fun `edits create new versions and never overwrite`() = runTest {
        val first = (save(null, validDraft(ContractType.LEASE)) as AppResult.Success).value
        val edited = validDraft(ContractType.LEASE).copy(amount = "16000")
        val second = (save(first.contractId, edited) as AppResult.Success).value
        assertEquals(1, first.version)
        assertEquals(2, second.version)
        assertEquals(first.contractId, second.contractId)
        assertTrue("FIFTEEN THOUSAND" in contracts.versions.value.first { it.id == first.id }.body)
        assertTrue("SIXTEEN THOUSAND" in second.body)
    }

    @Test
    fun `an invalid draft is never saved`() = runTest {
        val result = save(null, validDraft(ContractType.SALE).copy(witnessOne = ""))
        assertEquals(AppResult.Failure(AppError.Unexpected("invalid_draft")), result)
        assertEquals(emptyList<Any>(), contracts.versions.value)
    }

    @Test
    fun `analysis runs on the exact generated pages and is linked to the version`() = runTest {
        val version = (save(null, validDraft(ContractType.LOAN)) as AppResult.Success).value
        val analyze = AnalyzeGeneratedContractUseCase(analyses, contracts, scheduler, FakeAnalytics())
        val id = (analyze(version, listOf("page one", "page two")) as AppResult.Success).value
        assertEquals(SourceKind.GENERATED, analyses.analyses.value.getValue(id).sourceKind)
        assertEquals(listOf("page one", "page two"), analyses.texts.getValue(id).pages)
        assertEquals(listOf(id), scheduler.enqueued)
        assertEquals(id, contracts.observeVersion(version.id).first()!!.analysisId)
    }

    @Test
    fun `suggestions that cite law or set amounts are withheld and counted`() = runTest {
        assistant.suggestions = AppResult.Success(
            listOf(
                ClauseSuggestion("Pets", "The LESSEE shall keep no pets in the Premises."),
                ClauseSuggestion("Rescission", "Under Article 1659 the LESSOR may rescind."),
                ClauseSuggestion("Penalty", "A penalty of PHP 1,000 applies to late rent."),
                ClauseSuggestion("pets", "Duplicate title, dropped."),
            )
        )
        val suggest = SuggestClausesUseCase(assistant, FakeAnalytics())
        val result = (suggest(validDraft(ContractType.LEASE)) as AppResult.Success).value
        assertEquals(listOf("Pets"), result.proposals.map { it.clause.title })
        assertEquals(2, result.withheld)
        val proposal = result.proposals.single()
        assertEquals(OptionalClause.Source.ASSISTANT, proposal.clause.source)
        assertEquals(null, proposal.before)
        assertEquals(listOf(DiffSegment(DiffSegment.Type.ADDED, proposal.clause.text)), proposal.diff)
    }

    @Test
    fun `existing clause titles are not suggested again`() = runTest {
        val existing = OptionalClause("lib:no_pets", "Pets", "No pets.", OptionalClause.Source.LIBRARY)
        assistant.suggestions = AppResult.Success(listOf(ClauseSuggestion("PETS", "The LESSEE shall keep no pets.")))
        val draft = validDraft(ContractType.LEASE).copy(optionalClauses = listOf(existing))
        val result = (SuggestClausesUseCase(assistant, FakeAnalytics())(draft) as AppResult.Success).value
        assertEquals(0, result.proposals.size)
    }

    @Test
    fun `rewrites are proposed as a diff, or withheld when they change substance`() = runTest {
        val text = "The LESSOR may inspect upon twenty-four (24) hours' notice."
        val clause = OptionalClause("lib:x", "Inspection", text, OptionalClause.Source.LIBRARY)
        val rewrite = RewriteClauseUseCase(assistant, FakeAnalytics())
        val draft = validDraft(ContractType.LEASE)

        assistant.rewrite = AppResult.Success("The LESSOR may inspect after giving twenty-four (24) hours' notice.")
        val proposed = (rewrite(draft, clause) as AppResult.Success).value as RewriteOutcome.Proposed
        assertEquals(clause.text, proposed.edit.before)
        assertEquals(clause.id, proposed.edit.clause.id)
        assertTrue(proposed.edit.diff.any { it.type == DiffSegment.Type.ADDED })

        assistant.rewrite = AppResult.Success("The LESSOR may inspect upon forty-eight (48) hours' notice.")
        val withheld = (rewrite(draft, clause) as AppResult.Success).value
        assertEquals(RewriteOutcome.Withheld(ClauseRejection.CHANGES_NUMBERS), withheld)

        assistant.rewrite = AppResult.Success(" ${clause.text} ")
        assertEquals(RewriteOutcome.Unchanged, (rewrite(draft, clause) as AppResult.Success).value)
    }

    @Test
    fun `the assistant is unavailable when switched off remotely or on a compromised device`() = runTest {
        fun availability(flags: FakeFeatureFlags, integrity: FakeIntegrityChecker) =
            AssistantAvailabilityUseCase(assistant, flags, integrity)
        val off = FakeFeatureFlags(FeatureFlags.DEFAULT.copy(llmEnabled = false))
        assertEquals(LlmAvailability.UNAVAILABLE, availability(off, FakeIntegrityChecker())())
        val compromised = FakeIntegrityChecker(DeviceIntegrity.COMPROMISED)
        assertEquals(LlmAvailability.UNAVAILABLE, availability(FakeFeatureFlags(), compromised)())
        assertEquals(LlmAvailability.READY, availability(FakeFeatureFlags(), FakeIntegrityChecker())())
    }

    @Test
    fun `saving a version is audited without its text`() = runTest {
        val saved = (save(null, validDraft(ContractType.SALE)) as AppResult.Success).value
        val entry = auditLog.entries.value.single()
        assertEquals(AuditEvent.CONTRACT_SAVED, entry.event)
        assertEquals(saved.id, entry.subjectId)
        assertEquals("SALE", entry.detail)
    }
}
