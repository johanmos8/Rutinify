package com.mirkwood.rutinify.testutil

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `las coroutines en Dispatchers Main usan el dispatcher de test`() = runTest(mainDispatcherRule.testDispatcher) {
        var ran = false
        CoroutineScope(Dispatchers.Main).launch { ran = true }
        assertThat(ran).isFalse()

        advanceUntilIdle()

        assertThat(ran).isTrue()
    }
}
