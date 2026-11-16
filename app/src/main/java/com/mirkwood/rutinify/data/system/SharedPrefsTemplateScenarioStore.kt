package com.mirkwood.rutinify.data.system

import android.content.Context
import androidx.core.content.edit
import com.mirkwood.rutinify.domain.importer.TemplateScenarioStore
import com.mirkwood.rutinify.domain.model.TemplateScenario
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One value in a private `SharedPreferences` file; no extra dependency needed (D-70). */
class SharedPrefsTemplateScenarioStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TemplateScenarioStore {

    private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

    // An unknown name (a scenario removed in a later version) counts as no choice
    override fun last(): TemplateScenario? =
        prefs.getString(KEY_SCENARIO, null)?.let { name -> TemplateScenario.entries.firstOrNull { it.name == name } }

    override fun save(scenario: TemplateScenario) {
        prefs.edit { putString(KEY_SCENARIO, scenario.name) }
    }

    private companion object {
        const val FILE = "templates"
        const val KEY_SCENARIO = "last_scenario"
    }
}
