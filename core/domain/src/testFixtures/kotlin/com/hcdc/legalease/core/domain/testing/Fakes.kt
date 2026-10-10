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
import com.hcdc.legalease.core.domain.model.Verdict
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
import kotlinx.coroutines.flow.Flow
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
    override suspend fun complete(id: String, verdict: Verdict) =
        update(id) { it.copy(status = AnalysisStatus.COMPLETED, verdict = verdict) }

    override suspend fun fail(id: String, error: AppError) =
        update(id) { it.copy(status = AnalysisStatus.FAILED, error = error) }

    override suspend fun markCancelled(id: String) = update(id) { it.copy(status = AnalysisStatus.CANCELLED) }

    /** Mirrors the DAO: finished, non-cancelled analyses matching name or classification. */
    fun historySnapshot(query: String): List<Analysis> = analyses.value.values.filter {
        (it.status == AnalysisStatus.COMPLETED || it.status == AnalysisStatus.FAILED) &&
            (it.documentName.contains(query, ignoreCase = true) ||
                it.verdict?.classification?.label?.contains(query, ignoreCase = true) == true)
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
