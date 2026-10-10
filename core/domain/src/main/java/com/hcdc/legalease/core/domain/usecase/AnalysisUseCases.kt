package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.AnalysisScheduler
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Records a new analysis and schedules it to run in the background. Returns its ID. */
class StartAnalysisUseCase @Inject constructor(
    private val repository: AnalysisRepository,
    private val scheduler: AnalysisScheduler,
) {
    suspend operator fun invoke(documentName: String, kind: SourceKind, sourceUris: List<String>): AppResult<String> {
        val created = repository.create(documentName, kind, sourceUris)
        if (created is AppResult.Success) scheduler.enqueue(created.value)
        return created
    }
}

class ObserveAnalysisUseCase @Inject constructor(private val repository: AnalysisRepository) {
    operator fun invoke(analysisId: String): Flow<Analysis?> = repository.observe(analysisId)
}

class CancelAnalysisUseCase @Inject constructor(
    private val repository: AnalysisRepository,
    private val scheduler: AnalysisScheduler,
) {
    suspend operator fun invoke(analysisId: String) {
        scheduler.cancel(analysisId)
        repository.markCancelled(analysisId)
    }
}

class GetHistoryUseCase @Inject constructor(private val repository: AnalysisRepository) {
    operator fun invoke(query: String): Flow<List<Analysis>> = repository.observeHistory(query.trim())
}

class DeleteAnalysisUseCase @Inject constructor(
    private val repository: AnalysisRepository,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(analysisId: String) {
        repository.delete(analysisId)
        syncScheduler.requestSync()
    }
}
