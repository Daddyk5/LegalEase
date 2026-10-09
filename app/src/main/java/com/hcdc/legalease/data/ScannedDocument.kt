package com.hcdc.legalease.data

/**
 * OCR output for one uploaded document.
 *
 * @property pages recognised text per page, in page order; an unreadable page is an empty string.
 * @property unreadablePages zero-based indexes of pages whose OCR failed or produced no text.
 */
data class ScannedDocument(
    val id: String,
    val name: String,
    val pages: List<String>,
    val unreadablePages: List<Int>,
) {
    val fullText: String
        get() = pages.filter { it.isNotBlank() }.joinToString(separator = "\n\n")
}
