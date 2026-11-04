package com.mirkwood.rutinify.data.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Loads JSON fixtures from `src/test/resources/fixtures/`. */
internal object Fixtures {

    fun text(name: String): String {
        val resource = requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }
        return resource.readText(Charsets.UTF_8)
    }

    fun json(name: String): JsonObject = Json.parseToJsonElement(text(name)).jsonObject
}
