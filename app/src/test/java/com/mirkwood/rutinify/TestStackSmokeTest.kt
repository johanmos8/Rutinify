package com.mirkwood.rutinify

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** T-01: checks that Truth, Turbine, coroutines-test and kotlinx.serialization are on the test classpath. */
class TestStackSmokeTest {

    @Test
    fun `truth turbine y coroutines-test funcionan juntos`() = runTest {
        flowOf(1, 2).test {
            assertThat(awaitItem()).isEqualTo(1)
            assertThat(awaitItem()).isEqualTo(2)
            awaitComplete()
        }
    }

    @Test
    fun `kotlinx serialization parsea un JsonElement`() {
        val json = Json.parseToJsonElement("""{"schemaVersion": 1}""")
        assertThat(json.jsonObject.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(1)
    }
}
