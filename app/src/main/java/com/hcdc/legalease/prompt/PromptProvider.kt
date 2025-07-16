package com.hcdc.legalease.prompt

object PromptProvider {
    fun buildPrompt(text: String): String {
        return "Analyze this contract and return a clause classification with JSON output: $text"
    }
}
