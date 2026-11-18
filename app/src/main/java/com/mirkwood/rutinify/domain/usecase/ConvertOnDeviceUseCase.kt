package com.mirkwood.rutinify.domain.usecase

import com.mirkwood.rutinify.domain.importer.ImportResult
import com.mirkwood.rutinify.domain.importer.OnDeviceModel
import com.mirkwood.rutinify.domain.importer.PromptTemplateSource
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import com.mirkwood.rutinify.domain.model.TemplateScenario
import javax.inject.Inject

/** Outcome of "Convertir en el teléfono" (R-22.2, R-22.5, R-22.6). */
sealed interface ConvertResult {
    /** The model answered: [json] as it wrote it, already validated like pasted text (R-22.2). */
    data class Converted(val json: String, val result: ImportResult) : ConvertResult

    /** R-22.6: too long for the model; nothing was sent. */
    data object TooLong : ConvertResult

    /** R-22.5: the model could not answer. */
    data object Failed : ConvertResult
}

/**
 * Writes the contract JSON with the phone's model, using the chosen scenario's template as instructions, and validates
 * it with the same importer as pasted text (R-22.2, D-80).
 */
class ConvertOnDeviceUseCase @Inject constructor(
    private val model: OnDeviceModel,
    private val templates: PromptTemplateSource,
    private val importer: RoutineImporter,
) {

    suspend fun isAvailable(): Boolean = model.isAvailable()

    suspend operator fun invoke(routine: String, scenario: TemplateScenario): ConvertResult {
        val instructions = instructionsOf(templates.load(scenario))
        if (!model.fits(instructions, routine)) return ConvertResult.TooLong
        val answer = model.generate(instructions, routine) ?: return ConvertResult.Failed
        return ConvertResult.Converted(answer, importer.import(answer))
    }

    /** The template without its last line, the "[PEGA AQUÍ TU RUTINA]" marker: the routine goes after it. */
    private fun instructionsOf(template: String): String {
        val lines = template.trimEnd().lines()
        return if (lines.lastOrNull()?.trim()?.let { it.startsWith("[") && it.endsWith("]") } == true) {
            lines.dropLast(1).joinToString("\n").trimEnd() + "\n"
        } else {
            template
        }
    }
}
