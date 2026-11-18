package com.mirkwood.rutinify.domain.importer

/** The phone's own language model (Gemini Nano through ML Kit GenAI); nothing leaves the phone (R-22.7, D-80). */
interface OnDeviceModel {

    /** True only when the model is ready to use right now (`AVAILABLE`); downloadable or missing is false (R-22.4). */
    suspend fun isAvailable(): Boolean

    /** Whether [instructions] plus [input] fit the model's input limit, leaving room for the answer (R-22.6). */
    suspend fun fits(instructions: String, input: String): Boolean

    /** The model's answer, or null if it failed (R-22.5). */
    suspend fun generate(instructions: String, input: String): String?
}
