package com.mirkwood.rutinify.data.importer

import android.content.Context
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.di.IoDispatcher
import com.mirkwood.rutinify.domain.importer.PromptTemplateSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads `res/raw/prompt_template.txt` (R-1.3). */
class RawPromptTemplateSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : PromptTemplateSource {

    override suspend fun load(): String = withContext(dispatcher) {
        context.resources.openRawResource(R.raw.prompt_template).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
