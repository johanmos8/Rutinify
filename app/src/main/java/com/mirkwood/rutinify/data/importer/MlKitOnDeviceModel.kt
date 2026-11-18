package com.mirkwood.rutinify.data.importer

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.domain.importer.OnDeviceModel
import javax.inject.Inject

/**
 * ML Kit GenAI Prompt API (D-80). A client per call, always closed, so no model stays loaded while the screen is not
 * converting. The template is the fixed prompt prefix (implicit prefix caching) and the routine the variable part.
 */
class MlKitOnDeviceModel @Inject constructor() : OnDeviceModel {

    override suspend fun isAvailable(): Boolean = withClient { model ->
        model.checkStatus() == FeatureStatus.AVAILABLE
    } ?: false

    override suspend fun fits(instructions: String, input: String): Boolean = withClient { model ->
        model.countTokens(request(instructions, input)).totalTokens + MAX_OUTPUT_TOKENS <= model.getTokenLimit()
    } ?: false

    override suspend fun generate(instructions: String, input: String): String? = withClient { model ->
        model.generateContent(request(instructions, input)).candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
    }

    private fun request(instructions: String, input: String): GenerateContentRequest =
        generateContentRequest(TextPart(input)) {
            promptPrefix = PromptPrefix(instructions)
            // Low temperature: the contract asks for exact JSON, not creativity
            temperature = TEMPERATURE
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }

    /** Runs [block] with a fresh client; a model error is logged and becomes null (cancellation still propagates). */
    private suspend fun <T> withClient(block: suspend (GenerativeModel) -> T): T? {
        val model = Generation.getClient()
        return try {
            block(model)
        } catch (e: GenAiException) {
            Log.w(LOG_TAG, "On-device model failed", e)
            null
        } finally {
            model.close()
        }
    }

    private companion object {
        const val TEMPERATURE = 0.2f

        /** Room for a JSON of a few dozen activities. */
        const val MAX_OUTPUT_TOKENS = 2048
    }
}
