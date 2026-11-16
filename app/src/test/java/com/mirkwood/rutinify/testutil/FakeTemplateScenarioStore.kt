package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.importer.TemplateScenarioStore
import com.mirkwood.rutinify.domain.model.TemplateScenario

class FakeTemplateScenarioStore(var saved: TemplateScenario? = null) : TemplateScenarioStore {
    override fun last(): TemplateScenario? = saved
    override fun save(scenario: TemplateScenario) {
        saved = scenario
    }
}
