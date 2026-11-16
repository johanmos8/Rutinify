package com.mirkwood.rutinify.domain.importer

import com.mirkwood.rutinify.domain.model.TemplateScenario

/** The prompt template of each scenario for the external AI, loaded from `res/raw` (R-1.3, R-16.1). */
fun interface PromptTemplateSource {
    suspend fun load(scenario: TemplateScenario): String
}
