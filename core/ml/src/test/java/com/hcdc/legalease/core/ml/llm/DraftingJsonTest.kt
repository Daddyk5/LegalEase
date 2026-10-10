package com.hcdc.legalease.core.ml.llm

import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftingJsonTest {

    @Test
    fun `parses suggestions, tolerating text around the object`() {
        val raw = "```json\n{\"clauses\":[{\"title\":\" Pets \",\"text\":\"No pets. \"}]}\n```"
        assertEquals(listOf(ClauseSuggestion("Pets", "No pets.")), DraftingJson.parseSuggestions(raw))
        assertEquals(emptyList<ClauseSuggestion>(), DraftingJson.parseSuggestions("{\"clauses\":[]}"))
    }

    @Test
    fun `rejects malformed or oversized output`() {
        assertNull(DraftingJson.parseSuggestions("no json"))
        assertNull(DraftingJson.parseSuggestions("{\"clauses\":[{\"title\":\"x\"}]}"))
        assertNull(DraftingJson.parseSuggestions("{\"clauses\":[],\"extra\":1}"))
        val four = List(4) { "{\"title\":\"t\",\"text\":\"x\"}" }.joinToString(",")
        assertNull(DraftingJson.parseSuggestions("{\"clauses\":[$four]}"))
    }

    @Test
    fun `parses a rewrite and rejects an empty one`() {
        assertEquals("Clear text.", DraftingJson.parseRewrite("{\"text\":\" Clear text. \"}"))
        assertNull(DraftingJson.parseRewrite("{\"text\":\"  \"}"))
    }
}
