package com.hcdc.legalease.core.ml

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.IOException

private const val VOCAB_ASSET = "vocab.json"

/**
 * Parses a token → id vocabulary.
 *
 * @throws ClassifierException.VocabUnavailable if the JSON is malformed or empty.
 */
fun parseVocab(json: String): Map<String, Int> {
    val vocab = try {
        Json.decodeFromString<Map<String, Int>>(json)
    } catch (e: IllegalArgumentException) {
        // SerializationException is a subclass of IllegalArgumentException.
        throw ClassifierException.VocabUnavailable("$VOCAB_ASSET is not a valid token map", e)
    }
    if (vocab.isEmpty()) throw ClassifierException.VocabUnavailable("$VOCAB_ASSET is empty")
    return vocab
}

/**
 * Loads the vocabulary bundled in assets.
 *
 * @throws ClassifierException.VocabUnavailable if the asset is missing, malformed, or empty.
 */
fun loadVocab(context: Context): Map<String, Int> {
    val json = try {
        context.assets.open(VOCAB_ASSET).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        throw ClassifierException.VocabUnavailable("$VOCAB_ASSET could not be read", e)
    }
    return parseVocab(json)
}
