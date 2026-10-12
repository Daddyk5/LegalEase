package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.PageImageRenderer
import com.hcdc.legalease.core.domain.engine.RenderedPage
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import javax.inject.Inject

/** Renders one page of an analysed document so a cited quote can be checked against the original. */
class GetDocumentPageUseCase @Inject constructor(
    private val repository: AnalysisRepository,
    private val renderer: PageImageRenderer,
) {
    suspend operator fun invoke(analysisId: String, page: Int): AppResult<RenderedPage> {
        val analysis = repository.get(analysisId) ?: return AppResult.Failure(AppError.DocumentNotFound)
        val paths = repository.sourcePaths(analysisId)
        if (paths.isEmpty()) return AppResult.Failure(AppError.DocumentNotFound)
        return renderer.render(analysis.sourceKind, paths, page)
    }
}
