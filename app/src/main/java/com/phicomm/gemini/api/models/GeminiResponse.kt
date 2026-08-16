package com.phicomm.gemini.api.models

data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

data class Candidate(
    val content: Content? = null
)

data class GeminiError(
    val message: String
)
