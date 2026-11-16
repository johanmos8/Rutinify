package com.mirkwood.rutinify.domain.importer

import com.mirkwood.rutinify.domain.model.TemplateScenario

/** Remembers, across launches, the last scenario chosen on New routine (R-16.2). */
interface TemplateScenarioStore {

    /** null until the user picks one. */
    fun last(): TemplateScenario?

    fun save(scenario: TemplateScenario)
}
