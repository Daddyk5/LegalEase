package com.hcdc.legalease.core.data.repository

import androidx.room.withTransaction
import com.hcdc.legalease.core.data.local.GeneratedContractDao
import com.hcdc.legalease.core.data.local.GeneratedContractEntity
import com.hcdc.legalease.core.data.local.LegalEaseDatabase
import com.hcdc.legalease.core.data.local.toDomain
import com.hcdc.legalease.core.data.local.DraftJson
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.repository.ContractRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

internal class OfflineFirstContractRepository @Inject constructor(
    private val database: LegalEaseDatabase,
    private val dao: GeneratedContractDao,
    private val clock: Clock,
) : ContractRepository {

    override suspend fun saveVersion(
        contractId: String?,
        draft: ContractDraft,
        title: String,
        body: String,
    ): GeneratedContract =
        database.withTransaction {
            val id = contractId ?: UUID.randomUUID().toString()
            val entity = GeneratedContractEntity(
                id = UUID.randomUUID().toString(),
                contractId = id,
                version = (dao.latestVersion(id) ?: 0) + 1,
                type = draft.type.name,
                title = title,
                body = body,
                createdAt = clock.millis(),
                needsSync = true,
                draftJson = DraftJson.encode(draft),
            )
            dao.insert(entity)
            entity.toDomain()
        }

    override fun observeLatest(): Flow<List<GeneratedContract>> =
        dao.observeLatest().map { rows -> rows.map { it.toDomain() } }

    override fun observeVersions(contractId: String): Flow<List<GeneratedContract>> =
        dao.observeVersions(contractId).map { rows -> rows.map { it.toDomain() } }

    override fun observeVersion(versionId: String): Flow<GeneratedContract?> =
        dao.observe(versionId).map { it?.toDomain() }

    override suspend fun linkAnalysis(versionId: String, analysisId: String) = dao.setAnalysisId(versionId, analysisId)
}
