package com.mirkwood.rutinify.testutil

import com.mirkwood.rutinify.domain.importer.OnDeviceModel
import kotlinx.coroutines.CompletableDeferred

/** The phone's model, scripted by the test; [hold] keeps [generate] waiting until completed. */
class FakeOnDeviceModel(
    var available: Boolean = true,
    var fits: Boolean = true,
    var answer: String? = null,
) : OnDeviceModel {

    var hold: CompletableDeferred<Unit>? = null
    val prompts = mutableListOf<Pair<String, String>>()

    override suspend fun isAvailable(): Boolean = available

    override suspend fun fits(instructions: String, input: String): Boolean = fits

    override suspend fun generate(instructions: String, input: String): String? {
        prompts += instructions to input
        hold?.await()
        return answer
    }
}
