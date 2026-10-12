package com.hcdc.legalease.core.data.account

import com.google.firebase.auth.FirebaseAuth
import com.hcdc.legalease.core.data.local.AnalysisDao
import com.hcdc.legalease.core.data.local.AnalysisEntity
import com.hcdc.legalease.core.data.local.AuditDao
import com.hcdc.legalease.core.data.local.AuditEntryEntity
import com.hcdc.legalease.core.data.local.DocumentPageDao
import com.hcdc.legalease.core.data.local.DraftJson
import com.hcdc.legalease.core.data.local.GeneratedContractDao
import com.hcdc.legalease.core.data.local.GeneratedContractEntity
import com.hcdc.legalease.core.data.storage.ScanSourceStore
import com.hcdc.legalease.core.domain.engine.ContractDocuments
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.generator.ContractTemplates
import com.hcdc.legalease.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Builds the "export my data" archive: `legalease-data.json` with every analysis (including OCR text
 * and the full report), contract version (text and form), setting, and audit entry; the uploaded
 * documents; and each contract version as a PDF.
 */
internal class DataExporter @Inject constructor(
    private val analysisDao: AnalysisDao,
    private val pageDao: DocumentPageDao,
    private val contractDao: GeneratedContractDao,
    private val auditDao: AuditDao,
    private val sources: ScanSourceStore,
    private val preferences: PreferencesRepository,
    private val documents: ContractDocuments,
    private val auth: FirebaseAuth,
    private val clock: Clock,
) {
    private val json = Json { prettyPrint = true }

    /** Writes the archive into [dir], replacing earlier exports there, and returns it. */
    suspend fun writeZip(dir: File): File {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()
        val day = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(clock.millis()))
        val zip = File(dir, "LegalEase-data-$day.zip")
        val scratch = File(dir, "tmp").apply { mkdirs() }
        try {
            ZipOutputStream(zip.outputStream().buffered()).use { out ->
                val analyses = analysisDao.allLive().map { analysis -> analysis.toJson(out) }
                val contracts = contractDao.all().map { contract -> contract.toJson(out, scratch) }
                val manifest = buildJsonObject {
                    put("format", "legalease-data-export")
                    put("formatVersion", FORMAT_VERSION)
                    put("exportedAt", Instant.ofEpochMilli(clock.millis()).toString())
                    put("account", account())
                    put("settings", settings())
                    put("analyses", buildJsonArray { analyses.forEach { add(it) } })
                    put("contracts", buildJsonArray { contracts.forEach { add(it) } })
                    put("auditLog", buildJsonArray { auditDao.all().forEach { add(it.toJson()) } })
                }
                val text = json.encodeToString(JsonObject.serializer(), manifest)
                out.entry("legalease-data.json") { it.write(text.toByteArray()) }
            }
        } finally {
            scratch.deleteRecursively()
        }
        return zip
    }

    private fun account() = buildJsonObject {
        val user = auth.currentUser
        put("uid", user?.uid)
        put("email", user?.email)
    }

    private suspend fun settings() = buildJsonObject {
        put("cloudSyncEnabled", preferences.cloudSyncEnabled.first())
        put("retention", preferences.retention.first().name)
        put("appLockEnabled", preferences.appLockEnabled.first())
        put("acceptedPrivacyNoticeVersion", preferences.acceptedPrivacyNoticeVersion.first())
    }

    private suspend fun AnalysisEntity.toJson(out: ZipOutputStream): JsonObject {
        val files = sources.paths(id).map(::File).mapIndexed { index, file ->
            val name = "documents/$id/${"%03d".format(index + 1)}${extensionOf(file)}"
            out.entry(name) { stream -> file.inputStream().use { it.copyTo(stream) } }
            name
        }
        val pages = pageDao.pages(id).map { it.text }
        return buildJsonObject {
            put("id", id)
            put("documentName", documentName)
            put("sourceKind", sourceKind)
            put("status", status)
            put("createdAt", Instant.ofEpochMilli(createdAt).toString())
            put("pageCount", pageCount)
            put("errorCode", errorCode)
            put("report", reportJson?.let { parseOrString(it) } ?: JsonNull)
            put("pageText", buildJsonArray { pages.forEach { add(JsonPrimitive(it)) } })
            put("files", buildJsonArray { files.forEach { add(JsonPrimitive(it)) } })
        }
    }

    private suspend fun GeneratedContractEntity.toJson(out: ZipOutputStream, scratch: File): JsonObject {
        val draft = draftJson?.let(DraftJson::decode)
        val rendered = draft?.let(ContractTemplates::render)
        val pdf = rendered?.let {
            val name = "contracts/${safe(title)} v$version ($id).pdf"
            val file = File(scratch, "$id.pdf")
            documents.write(it, PaperSize.A4, file)
            out.entry(name) { stream -> file.inputStream().use { input -> input.copyTo(stream) } }
            file.delete()
            name
        }
        return buildJsonObject {
            put("id", id)
            put("contractId", contractId)
            put("version", version)
            put("type", type)
            put("title", title)
            put("createdAt", Instant.ofEpochMilli(createdAt).toString())
            put("text", body)
            put("form", draftJson?.let { parseOrString(it) } ?: JsonNull)
            put("analysisId", analysisId)
            put("pdf", pdf)
        }
    }

    private fun AuditEntryEntity.toJson() = buildJsonObject {
        put("timestamp", Instant.ofEpochMilli(timestamp).toString())
        put("event", event)
        put("subjectId", subjectId)
        put("modelVersion", modelVersion)
        put("method", method)
        put("confidence", confidence)
        put("agreement", agreement)
        put("selfConsistency", selfConsistency)
        put("grounding", grounding)
        put("ocrQuality", ocrQuality)
        put("completeness", completeness)
        put("scoringVersion", scoringVersion)
        put("detail", detail)
    }

    private fun parseOrString(raw: String): JsonElement =
        runCatching { json.parseToJsonElement(raw) }.getOrElse { JsonPrimitive(raw) }

    private fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    /** Stored copies have no extension; name them by their first bytes so they open correctly. */
    private fun extensionOf(file: File): String {
        val head = ByteArray(HEADER_BYTES)
        val read = file.inputStream().use { it.read(head) }
        val bytes = head.copyOf(maxOf(read, 0))
        fun startsWith(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
        return when {
            startsWith('%'.code, 'P'.code, 'D'.code, 'F'.code) -> ".pdf"
            startsWith(JPEG_0, JPEG_1) -> ".jpg"
            startsWith(PNG_0, 'P'.code, 'N'.code, 'G'.code) -> ".png"
            bytes.size >= HEADER_BYTES &&
                String(bytes, WEBP_OFFSET, WEBP_LENGTH, Charsets.US_ASCII) == "WEBP" -> ".webp"
            else -> ""
        }
    }

    private fun safe(name: String) = name.replace(UNSAFE, "_").take(MAX_NAME).trim()

    private companion object {
        const val FORMAT_VERSION = 1
        const val HEADER_BYTES = 12
        const val JPEG_0 = 0xFF
        const val JPEG_1 = 0xD8
        const val PNG_0 = 0x89
        const val WEBP_OFFSET = 8
        const val WEBP_LENGTH = 4
        const val MAX_NAME = 80
        val UNSAFE = Regex("""[^A-Za-z0-9 ._-]""")
    }
}
