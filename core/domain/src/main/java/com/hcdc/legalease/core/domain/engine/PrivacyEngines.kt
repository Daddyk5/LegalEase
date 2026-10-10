package com.hcdc.legalease.core.domain.engine

import com.hcdc.legalease.core.domain.generator.RenderedContract
import com.hcdc.legalease.core.domain.model.AuditEntry
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Play Integrity check. Results are cached; [refresh] never throws. */
interface IntegrityChecker {
    val verdict: StateFlow<DeviceIntegrity>

    suspend fun refresh()
}

/** The local audit trail. Entries never contain document content. */
interface AuditLog {
    suspend fun record(entry: AuditEntry)

    fun observe(): Flow<List<AuditEntry>>

    suspend fun all(): List<AuditEntry>
}

/** Paper sizes in PostScript points (1/72 inch). */
enum class PaperSize(val widthPt: Int, val heightPt: Int) {
    /** 210 x 297 mm. */
    A4(widthPt = 595, heightPt = 842),

    /** 8.5 x 13 in, the "legal" (long bond) size used for contracts in the Philippines; US legal is 8.5 x 14. */
    LEGAL(widthPt = 612, heightPt = 936),
}

/** Lays out and writes contract PDFs. */
interface ContractDocuments {
    /** The body text of each page as laid out on [size]; what the analysis reads, so page numbers match. */
    suspend fun pageTexts(contract: RenderedContract, size: PaperSize = PaperSize.A4): List<String>

    /** Writes the PDF to app cache, replacing earlier exports, and returns it for sharing. */
    suspend fun export(contract: RenderedContract, size: PaperSize, fileName: String): File

    /** Writes the PDF to [target]. Used when bundling contracts into a data export. */
    suspend fun write(contract: RenderedContract, size: PaperSize, target: File)
}

/** The user's data as a whole: export, automatic retention, and account deletion. */
interface AccountDataRepository {
    /** Writes a ZIP of everything LegalEase holds about the user (JSON plus documents and PDFs). */
    suspend fun exportAll(): AppResult<File>

    /** Deletes analyses created before [cutoffMillis]. Returns how many were deleted. */
    suspend fun deleteAnalysesBefore(cutoffMillis: Long): Int

    /**
     * Deletes the user's cloud data, then the Firebase account, then everything on this device.
     * Fails with RecentLoginRequired if Firebase needs a fresh sign-in first; local data is kept then,
     * so the user can sign in again and retry.
     */
    suspend fun deleteAccountAndData(): AppResult<Unit>
}
