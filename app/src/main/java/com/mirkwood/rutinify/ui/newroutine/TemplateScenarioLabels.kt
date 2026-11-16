package com.mirkwood.rutinify.ui.newroutine

import androidx.annotation.StringRes
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.domain.model.TemplateScenario

/** Chip text of each scenario in the template selector (R-16.1). */
@StringRes
fun TemplateScenario.labelRes(): Int = when (this) {
    TemplateScenario.PUPPY -> R.string.template_scenario_puppy
    TemplateScenario.MEDICATION -> R.string.template_scenario_medication
    TemplateScenario.BABY -> R.string.template_scenario_baby
    TemplateScenario.STUDY -> R.string.template_scenario_study
    TemplateScenario.GENERIC -> R.string.template_scenario_generic
}
