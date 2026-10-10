package com.hcdc.legalease.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.hcdc.legalease.core.data.local.AnalysisDao
import com.hcdc.legalease.core.data.local.AnalysisEntity
import com.hcdc.legalease.core.data.local.DocumentPageDao
import com.hcdc.legalease.core.data.local.DocumentPageEntity
import com.hcdc.legalease.core.data.local.LegalEaseDatabase
import com.hcdc.legalease.core.data.local.encodePageList
import com.hcdc.legalease.core.data.local.escapeLike
import com.hcdc.legalease.core.data.local.toDomain
import com.hcdc.legalease.core.data.storage.ScanSourceStore
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.data.local.ReportJson
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Room is the source of truth; Firestore is only a sync target (see `SyncWorker`). */
internal class OfflineFirstAnalysisRepository @Inject constructor(
    private val database: LegalEaseDatabase,
    private val analysisDao: AnalysisDao,
    private val pageDao: DocumentPageDao,
    private val sources: ScanSourceStore,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnalysisRepository {

    override suspend fun create(documentName: String, kind: SourceKind, sourceUris: List<String>): AppResult<String> =
        withContext(ioDispatcher) {
            val id = UUID.randomUUID().toString()
            when (val copied = sources.copy(id, sourceUris)) {
                is AppResult.Failure -> copied
                is AppResult.Success -> {
                    val now = clock.millis()
                    analysisDao.upsert(
                        AnalysisEntity(
                            id = id,
                            documentName = documentName,
                            sourceKind = kind.name,
                            status = AnalysisStatus.QUEUED.name,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                    AppResult.Success(id)
                }
            }
        }

    override fun observe(id: String): Flow<Analysis?> = analysisDao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Analysis? = analysisDao.get(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun sourcePaths(id: String): List<String> = withContext(ioDispatcher) { sources.paths(id) }

    override suspend fun markExtracting(id: String, currentPage: Int, pageCount: Int) =
        analysisDao.markExtracting(id, currentPage, pageCount, clock.millis())

    /** The source copy is kept (app-private, excluded from backups) so cited pages can be opened. */
    override suspend fun saveExtractedText(id: String, text: ExtractedText) {
        database.withTransaction {
            pageDao.replace(
                id,
                text.pages.mapIndexed { index, page ->
                    DocumentPageEntity(id, index, page, text.pageQuality.getOrNull(index))
                },
            )
            analysisDao.setPageInfo(id, text.pages.size, encodePageList(text.unreadablePages))
        }
    }

    override suspend fun extractedText(id: String): ExtractedText? {
        val pages = pageDao.pages(id).takeIf { it.isNotEmpty() } ?: return null
        val texts = pages.map { it.text }
        val quality = pages.map { it.quality }
        return ExtractedText(
            pages = texts,
            unreadablePages = texts.indices.filter { texts[it].isBlank() },
            pageQuality = if (quality.all { it != null }) quality.map { it!! } else emptyList(),
        )
    }

    override suspend fun markClassifying(id: String) =
        analysisDao.setStatus(id, AnalysisStatus.CLASSIFYING.name, clock.millis())

    override suspend fun markAnalyzingPage(id: String, currentPage: Int, pageCount: Int) =
        analysisDao.markAnalyzingPage(id, currentPage, pageCount, clock.millis())

    override suspend fun markChecking(id: String) =
        analysisDao.setStatus(id, AnalysisStatus.CHECKING.name, clock.millis())

    override suspend fun complete(id: String, report: AnalysisReport) = analysisDao.complete(
        id = id,
        shownClassification = report.shownClassification?.label,
        confidence = report.confidence.total,
        summary = report.summary,
        reportJson = ReportJson.encode(report),
        contractType = report.contractType.name,
        scoringVersion = ReportJson.SCORING_VERSION,
        now = clock.millis(),
    )

    override suspend fun fail(id: String, error: AppError) {
        analysisDao.fail(id, error.code, clock.millis())
        withContext(ioDispatcher) { sources.delete(id) }
    }

    override suspend fun markCancelled(id: String) {
        analysisDao.setStatus(id, AnalysisStatus.CANCELLED.name, clock.millis())
        withContext(ioDispatcher) { sources.delete(id) }
    }

    override fun history(query: String): Flow<PagingData<Analysis>> =
        Pager(PagingConfig(pageSize = HISTORY_PAGE_SIZE)) { analysisDao.history(escapeLike(query)) }
            .flow
            .map { page -> page.map { it.toDomain() } }

    /**
     * Removes the document text and files immediately. If the analysis was ever uploaded, a
     * content-free tombstone is kept until sync propagates the deletion; otherwise the row goes now.
     */
    override suspend fun delete(id: String) {
        val entity = analysisDao.get(id) ?: return
        database.withTransaction {
            pageDao.delete(id)
            if (entity.remoteUpdatedAt == null) {
                analysisDao.hardDelete(id)
            } else {
                analysisDao.markDeleted(id, clock.millis())
            }
        }
        withContext(ioDispatcher) { sources.delete(id) }
    }

    private companion object {
        const val HISTORY_PAGE_SIZE = 20
    }
}
