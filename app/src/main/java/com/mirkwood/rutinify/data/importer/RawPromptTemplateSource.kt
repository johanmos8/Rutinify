package com.mirkwood.rutinify.data.importer

import android.content.Context
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.di.IoDispatcher
import com.mirkwood.rutinify.domain.importer.PromptTemplateSource
import com.mirkwood.rutinify.domain.model.TemplateScenario
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads `res/raw/prompt_template_<scenario>.txt` (R-1.3, R-16.1, D-70). */
class RawPromptTemplateSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : PromptTemplateSource {

    override suspend fun load(scenario: TemplateScenario): String = withContext(dispatcher) {
        context.resources.openRawResource(resourceOf(scenario)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun resourceOf(scenario: TemplateScenario): Int = when (scenario) {
        TemplateScenario.GENERIC -> R.raw.prompt_template_generic
        TemplateScenario.PUPPY -> R.raw.prompt_template_puppy
        TemplateScenario.MEDICATION -> R.raw.prompt_template_medication
        TemplateScenario.BABY -> R.raw.prompt_template_baby
        TemplateScenario.STUDY -> R.raw.prompt_template_study
    }
}
