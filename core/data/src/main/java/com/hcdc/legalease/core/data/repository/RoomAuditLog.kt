package com.hcdc.legalease.core.data.repository

import com.hcdc.legalease.core.data.local.AuditDao
import com.hcdc.legalease.core.data.local.AuditEntryEntity
import com.hcdc.legalease.core.domain.engine.AuditLog
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AuditEntry
import com.hcdc.legalease.core.domain.model.AuditEvent
import com.hcdc.legalease.core.domain.model.ConfidenceComponents
import com.hcdc.legalease.core.domain.model.ConfidenceScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The audit trail in the encrypted database. Kept until the user deletes their data. */
internal class RoomAuditLog @Inject constructor(private val dao: AuditDao) : AuditLog {

    override suspend fun record(entry: AuditEntry) = dao.insert(entry.toEntity())

    override fun observe(): Flow<List<AuditEntry>> = dao.observe().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun all(): List<AuditEntry> = dao.all().mapNotNull { it.toDomain() }
}

internal fun AuditEntry.toEntity(): AuditEntryEntity {
    val c = confidence?.components
    return AuditEntryEntity(
        timestamp = timestampMillis,
        event = event.name,
        subjectId = subjectId,
        modelVersion = modelVersion,
        method = method?.name,
        confidence = confidence?.total,
        agreement = c?.agreement,
        selfConsistency = c?.selfConsistency,
        grounding = c?.grounding,
        ocrQuality = c?.ocrQuality,
        completeness = c?.completeness,
        scoringVersion = scoringVersion,
        detail = detail,
    )
}

/** Rows with an event name this version does not know are skipped rather than guessed. */
internal fun AuditEntryEntity.toDomain(): AuditEntry? {
    val event = AuditEvent.entries.firstOrNull { it.name == event } ?: return null
    // Components are stored together; a partial set is treated as absent rather than filled in.
    val parts = listOfNotNull(agreement, selfConsistency, grounding, ocrQuality, completeness)
    val components = parts.takeIf { it.size == COMPONENT_COUNT }
        ?.let { ConfidenceComponents(it[0], it[1], it[2], it[COMPONENT_COUNT - 2], it[COMPONENT_COUNT - 1]) }
    return AuditEntry(
        timestampMillis = timestamp,
        event = event,
        subjectId = subjectId,
        modelVersion = modelVersion,
        method = AnalysisMethod.entries.firstOrNull { it.name == method },
        confidence = confidence?.let { ConfidenceScore(it, components) },
        scoringVersion = scoringVersion,
        detail = detail,
    )
}

private const val COMPONENT_COUNT = 5
