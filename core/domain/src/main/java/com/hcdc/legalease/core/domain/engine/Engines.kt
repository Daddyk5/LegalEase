package com.hcdc.legalease.core.domain.engine

import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppResult

/** On-device OCR. */
interface TextExtractor {
    /**
     * Reads every page of the document at [paths], one page at a time.
     *
     * @param onPage called with the 1-based page number and total before each page is read.
     */
    suspend fun extract(
        kind: SourceKind,
        paths: List<String>,
        onPage: suspend (page: Int, pageCount: Int) -> Unit,
    ): AppResult<ExtractedText>
}

/** On-device contract classifier. */
interface ContractClassifier {
    /** Downloads and loads the model if needed. Safe to call repeatedly. */
    suspend fun prepare(): AppResult<Unit>

    suspend fun classify(text: String): AppResult<Prediction>
}

/** Runs analyses in the background so they survive the app leaving the foreground. */
interface AnalysisScheduler {
    fun enqueue(analysisId: String)
    fun cancel(analysisId: String)
}

/** Requests a cloud sync; implementations no-op unless the user is signed in and has opted in. */
interface SyncScheduler {
    fun requestSync()
}
