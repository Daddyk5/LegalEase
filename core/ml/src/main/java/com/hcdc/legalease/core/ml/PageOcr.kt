package com.hcdc.legalease.core.ml

import com.google.mlkit.common.MlKitException
import com.hcdc.legalease.core.domain.model.ExtractedText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs OCR over [pageCount] pages strictly one at a time, so at most one page bitmap
 * is in memory. A page that fails OCR is recorded as unreadable instead of aborting
 * the whole document.
 *
 * @param onPage called with the 1-based page number before that page is read.
 * @param readPage returns the recognised text of the zero-based page.
 */
suspend fun ocrPages(
    pageCount: Int,
    onPage: suspend (pageNumber: Int) -> Unit,
    readPage: suspend (index: Int) -> String,
): ExtractedText {
    val pages = ArrayList<String>(pageCount)
    val unreadable = mutableListOf<Int>()
    for (index in 0 until pageCount) {
        currentCoroutineContext().ensureActive()
        onPage(index + 1)
        val text = try {
            readPage(index)
        } catch (_: MlKitException) {
            ""
        }
        pages += text
        if (text.isBlank()) unreadable += index
    }
    return ExtractedText(pages, unreadable)
}
