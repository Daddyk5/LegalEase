package com.hcdc.legalease.core.domain.testing

import com.hcdc.legalease.core.domain.engine.AnalysisScheduler
import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.engine.TextExtractor
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.hcdc.legalease.core.domain.model.AuthUser
import com.hcdc.legalease.core.domain.repository.AuthRepository
import com.hcdc.legalease.core.domain.telemetry.AnalyticsEvent
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import com.hcdc.legalease.core.domain.telemetry.PerformanceTrace
import com.hcdc.legalease.core.domain.telemetry.PerformanceTracer
import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.LlmAnalyzer
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.engine.LlmReasoning
import com.hcdc.legalease.core.domain.legal.TextChunk
import com.hcdc.legalease.core.domain.repository.FeatureFlags
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.repository.ContractRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val COMPLETE = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))

class FakeAnalysisRepository : AnalysisRepository {
    val analyses = MutableStateFlow<Map<String, Analysis>>(emptyMap())
    val sources = mutableMapOf<String, List<String>>()
    val texts = mutableMapOf<String, ExtractedText>()
    var createResult: AppResult<String>? = null
    private var nextId = 0

    fun put(analysis: Analysis) {
        analyses.value += analysis.id to analysis
    }

    private fun update(id: String, block: (Analysis) -> Analysis) {
        analyses.value[id]?.let { put(block(it)) }
    }

    override suspend fun create(documentName: String, kind: SourceKind, sourceUris: List<String>): AppResult<String> {
        createResult?.let { return it }
        val id = "id-${nextId++}"
        put(Analysis(id, documentName, kind, AnalysisStatus.QUEUED, createdAtMillis = 0, updatedAtMillis = 0))
        sources[id] = sourceUris
        return AppResult.Success(id)
    }

    override suspend fun createFromText(documentName: String, pages: List<String>): AppResult<String> {
        createResult?.let { return it }
        val id = "id-${nextId++}"
        put(Analysis(id, documentName, SourceKind.GENERATED, AnalysisStatus.QUEUED, 0, 0))
        texts[id] = ExtractedText(pages, unreadablePages = emptyList(), pageQuality = pages.map { 1f })
        return AppResult.Success(id)
    }

    override fun observe(id: String): Flow<Analysis?> = analyses.map { it[id] }
    override suspend fun get(id: String): Analysis? = analyses.value[id]
    override suspend fun sourcePaths(id: String): List<String> = sources[id].orEmpty()

    override suspend fun markExtracting(id: String, currentPage: Int, pageCount: Int) = update(id) {
        it.copy(status = AnalysisStatus.EXTRACTING_TEXT, currentPage = currentPage, pageCount = pageCount)
    }

    override suspend fun saveExtractedText(id: String, text: ExtractedText) {
        texts[id] = text
        sources.remove(id)
    }

    override suspend fun extractedText(id: String): ExtractedText? = texts[id]
    override suspend fun markClassifying(id: String) = update(id) { it.copy(status = AnalysisStatus.CLASSIFYING) }
    override suspend fun markAnalyzingPage(id: String, currentPage: Int, pageCount: Int) = update(id) {
        it.copy(status = AnalysisStatus.ANALYZING_PAGES, currentPage = currentPage, pageCount = pageCount)
    }
    override suspend fun markChecking(id: String) = update(id) { it.copy(status = AnalysisStatus.CHECKING) }
    override suspend fun complete(id: String, report: AnalysisReport) =
        update(id) { it.copy(status = AnalysisStatus.COMPLETED, report = report) }

    override suspend fun fail(id: String, error: AppError) =
        update(id) { it.copy(status = AnalysisStatus.FAILED, error = error) }

    override suspend fun markCancelled(id: String) = update(id) { it.copy(status = AnalysisStatus.CANCELLED) }

    /** Mirrors the DAO: finished, non-cancelled analyses matching name or classification. */
    fun historySnapshot(query: String): List<Analysis> = analyses.value.values.filter {
        (it.status == AnalysisStatus.COMPLETED || it.status == AnalysisStatus.FAILED) &&
            (it.documentName.contains(query, ignoreCase = true) ||
                it.report?.shownClassification?.label?.contains(query, ignoreCase = true) == true)
    }.sortedByDescending { it.createdAtMillis }

    override fun history(query: String): Flow<PagingData<Analysis>> =
        // Fully loaded, like a finished PagingSource; without this, paging-testing's asSnapshot waits forever.
        analyses.map { PagingData.from(historySnapshot(query), COMPLETE) }

    override suspend fun delete(id: String) {
        analyses.value -= id
    }
}

class FakeTextExtractor(var result: AppResult<ExtractedText>) : TextExtractor {
    var calls = 0
    override suspend fun extract(
        kind: SourceKind,
        paths: List<String>,
        onPage: suspend (page: Int, pageCount: Int) -> Unit,
    ): AppResult<ExtractedText> {
        calls++
        paths.indices.forEach { onPage(it + 1, paths.size) }
        return result
    }
}

class FakeClassifier(var result: AppResult<Prediction>) : ContractClassifier {
    val classifiedTexts = mutableListOf<String>()
    override suspend fun prepare(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun classify(text: String): AppResult<Prediction> {
        classifiedTexts += text
        return result
    }
}

class FakeAnalysisScheduler : AnalysisScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun enqueue(analysisId: String) { enqueued += analysisId }
    override fun cancel(analysisId: String) { cancelled += analysisId }
}

class FakeSyncScheduler : SyncScheduler {
    var requests = 0
    override fun requestSync() { requests++ }
}

class FakeAnalytics : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun log(event: AnalyticsEvent) { events += event }
}

class FakeTracer : PerformanceTracer {
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()
    val attributes = mutableMapOf<String, String>()
    val metrics = mutableMapOf<String, Long>()

    override fun start(name: String): PerformanceTrace {
        started += name
        return object : PerformanceTrace {
            override fun putAttribute(name: String, value: String) { attributes[name] = value }
            override fun putMetric(name: String, value: Long) { metrics[name] = value }
            override fun stop() { stopped += name }
        }
    }
}

class FakeAuthRepository : AuthRepository {
    val user = MutableStateFlow<AuthUser?>(null)
    var signInResult: AppResult<AuthUser> = AppResult.Failure(AppError.InvalidCredentials)
    var registerResult: AppResult<Unit> = AppResult.Success(Unit)
    var resetResult: AppResult<Unit> = AppResult.Success(Unit)
    var resendResult: AppResult<Unit> = AppResult.Success(Unit)
    val calls = mutableListOf<String>()

    override val currentUser: Flow<AuthUser?> = user

    override suspend fun signIn(email: String, password: String): AppResult<AuthUser> {
        calls += "signIn:$email"
        return signInResult.also { if (it is AppResult.Success) user.value = it.value }
    }

    override suspend fun register(email: String, password: String): AppResult<Unit> {
        calls += "register:$email"
        return registerResult
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> {
        calls += "reset:$email"
        return resetResult
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<AuthUser> {
        calls += "google"
        return signInResult.also { if (it is AppResult.Success) user.value = it.value }
    }

    override suspend fun resendVerification(): AppResult<Unit> {
        calls += "resend"
        return resendResult
    }

    override suspend fun signOut() {
        calls += "signOut"
        user.value = null
    }
}

/** Scripted language model: facts per page and a queue of reasoning results. */
class FakeLlmAnalyzer(
    var availability: LlmAvailability = LlmAvailability.READY,
    var facts: (TextChunk) -> AppResult<ChunkFacts> = { AppResult.Failure(AppError.Unexpected("no facts scripted")) },
    val reasoning: ArrayDeque<AppResult<LlmReasoning>> = ArrayDeque(),
) : LlmAnalyzer {
    var released = 0
    val reasoningTemperatures = mutableListOf<Float>()

    override suspend fun availability() = availability
    override suspend fun extractFacts(chunk: TextChunk) = facts(chunk)
    override suspend fun reason(facts: List<ChunkFacts>, temperature: Float): AppResult<LlmReasoning> {
        reasoningTemperatures += temperature
        return reasoning.removeFirstOrNull() ?: AppResult.Failure(AppError.Unexpected("no reasoning scripted"))
    }
    override suspend fun release() { released++ }
    override suspend fun warmUp(): AppResult<Unit> = AppResult.Success(Unit)
}

class FakeFeatureFlags(initial: FeatureFlags = FeatureFlags.DEFAULT) : FeatureFlagsRepository {
    val state = MutableStateFlow(initial)
    override val flags: StateFlow<FeatureFlags> = state
    override suspend fun refresh() = Unit
}

/** A minimal report; [total] decides whether [classification] is shown. */
fun sampleReport(
    classification: com.hcdc.legalease.core.domain.model.Classification?,
    total: Float = 0.9f,
    method: com.hcdc.legalease.core.domain.model.AnalysisMethod =
        com.hcdc.legalease.core.domain.model.AnalysisMethod.LANGUAGE_MODEL,
) = AnalysisReport(
    candidate = classification,
    confidence = com.hcdc.legalease.core.domain.model.ConfidenceScore(total, null),
    summary = "s",
    contractTitle = null,
    contractType = com.hcdc.legalease.core.domain.model.DetectedContractType.LEASE,
    reasons = emptyList(),
    missingElements = emptyList(),
    riskFlags = emptyList(),
    recommendations = emptyList(),
    unclear = emptyList(),
    pageCount = 1,
    skippedPages = emptyList(),
    poorQualityPages = emptyList(),
    method = method,
)

class FakeContractRepository : ContractRepository {
    val versions = MutableStateFlow<List<GeneratedContract>>(emptyList())
    private var nextId = 0

    override suspend fun saveVersion(
        contractId: String?,
        draft: com.hcdc.legalease.core.domain.model.ContractDraft,
        title: String,
        body: String,
    ): GeneratedContract {
        val id = contractId ?: "contract-${nextId++}"
        val version = (versions.value.filter { it.contractId == id }.maxOfOrNull { it.version } ?: 0) + 1
        val saved = GeneratedContract("v-${nextId++}", id, version, draft.type, title, body, 0, draft)
        versions.value += saved
        return saved
    }

    override fun observeLatest(): Flow<List<GeneratedContract>> = versions.map { all ->
        all.groupBy { it.contractId }.map { (_, v) -> v.maxBy { it.version } }
    }

    override fun observeVersions(contractId: String): Flow<List<GeneratedContract>> =
        versions.map { all -> all.filter { it.contractId == contractId }.sortedByDescending { it.version } }

    override fun observeVersion(versionId: String): Flow<GeneratedContract?> =
        versions.map { all -> all.firstOrNull { it.id == versionId } }

    override suspend fun linkAnalysis(versionId: String, analysisId: String) {
        versions.value = versions.value.map { if (it.id == versionId) it.copy(analysisId = analysisId) else it }
    }
}

class FakeContractAssistant(
    var availability: com.hcdc.legalease.core.domain.engine.LlmAvailability =
        com.hcdc.legalease.core.domain.engine.LlmAvailability.READY,
) : com.hcdc.legalease.core.domain.engine.ContractAssistant {
    var suggestions: AppResult<List<com.hcdc.legalease.core.domain.engine.ClauseSuggestion>> =
        AppResult.Success(emptyList())
    var rewrite: AppResult<String> = AppResult.Success("")
    var released = 0

    override suspend fun availability() = availability

    override suspend fun suggestClauses(type: com.hcdc.legalease.core.domain.model.ContractType, contractText: String) =
        suggestions

    override suspend fun rewriteForClarity(type: com.hcdc.legalease.core.domain.model.ContractType, clause: String) =
        rewrite

    override suspend fun release() {
        released++
    }
}
