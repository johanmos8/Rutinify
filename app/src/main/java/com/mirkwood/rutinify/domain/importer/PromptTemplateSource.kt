package com.mirkwood.rutinify.domain.importer

/** The prompt template for the external AI, loaded from `res/raw` (R-1.3). */
fun interface PromptTemplateSource {
    suspend fun load(): String
}
