package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.AnalysisScheduler
import com.hcdc.legalease.core.domain.engine.AuditLog
import com.hcdc.legalease.core.domain.engine.ContractDocuments
import com.hcdc.legalease.core.domain.engine.IntegrityChecker
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import com.hcdc.legalease.core.domain.engine.ContractAssistant
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.generator.ClauseGuard
import com.hcdc.legalease.core.domain.generator.ClauseRejection
import com.hcdc.legalease.core.domain.generator.ContractTemplates
import com.hcdc.legalease.core.domain.generator.DiffSegment
import com.hcdc.legalease.core.domain.generator.WordDiff
import com.hcdc.legalease.core.domain.model.AuditEntry
import com.hcdc.legalease.core.domain.model.AuditEvent
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.repository.ContractRepository
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.telemetry.AnalyticsEvent
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Renders [draft] with its template and saves it as a new version; never overwrites an earlier one.
 * Fails with [AppError.Unexpected] ("invalid_draft") if the draft does not validate.
 */
class SaveContractVersionUseCase @Inject constructor(
    private val repository: ContractRepository,
    private val syncScheduler: SyncScheduler,
    private val analytics: AnalyticsTracker,
    private val auditLog: AuditLog,
    private val clock: Clock,
) {
    suspend operator fun invoke(contractId: String?, draft: ContractDraft): AppResult<GeneratedContract> {
        val rendered = ContractTemplates.render(draft) ?: return AppResult.Failure(AppError.Unexpected("invalid_draft"))
        val saved = repository.saveVersion(contractId, draft, rendered.title, rendered.plainText)
        syncScheduler.requestSync()
        analytics.log(AnalyticsEvent.ContractSaved(draft.type.name, saved.version))
        auditLog.record(
            AuditEntry(clock.millis(), AuditEvent.CONTRACT_SAVED, subjectId = saved.id, detail = draft.type.name)
        )
        return AppResult.Success(saved)
    }
}

class GetContractsUseCase @Inject constructor(private val repository: ContractRepository) {
    operator fun invoke(): Flow<List<GeneratedContract>> = repository.observeLatest()
}

class ObserveContractVersionUseCase @Inject constructor(private val repository: ContractRepository) {
    operator fun invoke(versionId: String): Flow<GeneratedContract?> = repository.observeVersion(versionId)
}

/**
 * Runs a saved version through the same analysis as an uploaded contract. [pages] is the text of
 * each exported page, so the analysis cites the same page numbers the user sees in the PDF.
 */
class AnalyzeGeneratedContractUseCase @Inject constructor(
    private val analyses: AnalysisRepository,
    private val contracts: ContractRepository,
    private val scheduler: AnalysisScheduler,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(version: GeneratedContract, pages: List<String>): AppResult<String> {
        val name = "${ContractTemplates.shortName(version.type)} (version ${version.version})"
        val created = analyses.createFromText(name, pages)
        if (created is AppResult.Success) {
            contracts.linkAnalysis(version.id, created.value)
            scheduler.enqueue(created.value)
            analytics.log(AnalyticsEvent.ScanStarted("GENERATED"))
        }
        return created
    }
}

/** An assistant edit for the user to accept or discard. [before] is null for a new clause. */
data class ProposedEdit(
    val clause: OptionalClause,
    val before: String?,
    val diff: List<DiffSegment>,
)

data class Suggestions(val proposals: List<ProposedEdit>, val withheld: Int)

/** Whether the drafting assistant can run now: the model is ready and not switched off remotely. */
class AssistantAvailabilityUseCase @Inject constructor(
    private val assistant: ContractAssistant,
    private val featureFlags: FeatureFlagsRepository,
    private val integrity: IntegrityChecker,
) {
    /** Unavailable when switched off remotely or on a device that failed the integrity check. */
    suspend operator fun invoke(): LlmAvailability {
        val allowed = featureFlags.flags.value.llmEnabled && integrity.verdict.value != DeviceIntegrity.COMPROMISED
        return if (allowed) assistant.availability() else LlmAvailability.UNAVAILABLE
    }
}

/** Writes a saved version as a PDF for sharing, and records the export in the audit log. */
class ExportContractUseCase @Inject constructor(
    private val documents: ContractDocuments,
    private val analytics: AnalyticsTracker,
    private val auditLog: AuditLog,
    private val clock: Clock,
) {
    suspend operator fun invoke(version: GeneratedContract, size: PaperSize): AppResult<File> {
        val draft = version.draft ?: return AppResult.Failure(AppError.Unexpected("missing_draft"))
        val rendered = ContractTemplates.render(draft) ?: return AppResult.Failure(AppError.Unexpected("invalid_draft"))
        val name = "${ContractTemplates.shortName(version.type)} v${version.version} (${size.name}).pdf"
        return try {
            val file = documents.export(rendered, size, name)
            analytics.log(AnalyticsEvent.ContractExported(version.type.name, size.name))
            auditLog.record(
                AuditEntry(clock.millis(), AuditEvent.CONTRACT_EXPORTED, subjectId = version.id, detail = size.name)
            )
            AppResult.Success(file)
        } catch (_: IOException) {
            AppResult.Failure(AppError.StorageFull)
        }
    }
}

/**
 * Asks the assistant for optional clauses. Each suggestion passes [ClauseGuard]; rejected ones are
 * counted, never shown. Suggestions that repeat a clause already in the draft are dropped.
 */
class SuggestClausesUseCase @Inject constructor(
    private val assistant: ContractAssistant,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(draft: ContractDraft): AppResult<Suggestions> {
        val rendered = ContractTemplates.render(draft) ?: return AppResult.Failure(AppError.Unexpected("invalid_draft"))
        return when (val result = assistant.suggestClauses(draft.type, rendered.plainText)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val existing = draft.optionalClauses.map { it.title.trim().lowercase() }.toSet()
                val fresh = result.value
                    .filter { it.title.trim().lowercase() !in existing }
                    .distinctBy { it.title.trim().lowercase() }
                val (ok, rejected) = fresh.partition { ClauseGuard.checkSuggestion(it.title, it.text) == null }
                val proposals = ok.map { it.toProposal() }
                analytics.log(AnalyticsEvent.ClauseAssist("suggest", "offered", proposals.size))
                if (rejected.isNotEmpty()) {
                    analytics.log(AnalyticsEvent.ClauseAssist("suggest", "withheld", rejected.size))
                }
                AppResult.Success(Suggestions(proposals, withheld = rejected.size))
            }
        }
    }

    private fun ClauseSuggestion.toProposal(): ProposedEdit {
        val clause = OptionalClause(
            id = "ai:${UUID.randomUUID()}",
            title = title.trim(),
            text = text.trim(),
            source = OptionalClause.Source.ASSISTANT,
        )
        return ProposedEdit(clause, before = null, diff = WordDiff.diff("", clause.text))
    }
}

/** The outcome of a rewrite request: an edit to review, or the reason it was withheld. */
sealed interface RewriteOutcome {
    data class Proposed(val edit: ProposedEdit) : RewriteOutcome
    data class Withheld(val reason: ClauseRejection) : RewriteOutcome
    data object Unchanged : RewriteOutcome
}

/**
 * Rewords one optional clause for clarity. Mandatory template clauses cannot be passed here: only an
 * [OptionalClause] can. The result must keep every number and party reference ([ClauseGuard]).
 */
class RewriteClauseUseCase @Inject constructor(
    private val assistant: ContractAssistant,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(draft: ContractDraft, clause: OptionalClause): AppResult<RewriteOutcome> =
        when (val result = assistant.rewriteForClarity(draft.type, clause.text)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val rewritten = result.value.trim()
                val rejection = ClauseGuard.checkRewrite(clause.text, rewritten)
                val outcome = when {
                    rejection != null -> RewriteOutcome.Withheld(rejection)
                    !WordDiff.changes(clause.text, rewritten) -> RewriteOutcome.Unchanged
                    else -> RewriteOutcome.Proposed(
                        ProposedEdit(
                            clause = clause.copy(text = rewritten, source = OptionalClause.Source.ASSISTANT),
                            before = clause.text,
                            diff = WordDiff.diff(clause.text, rewritten),
                        )
                    )
                }
                analytics.log(
                    AnalyticsEvent.ClauseAssist(
                        "rewrite",
                        if (outcome is RewriteOutcome.Withheld) "withheld" else "offered",
                        1,
                    )
                )
                AppResult.Success(outcome)
            }
        }
}
