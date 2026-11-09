package com.mirkwood.rutinify.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.newroutine.NewRoutineScreen
import com.mirkwood.rutinify.ui.newroutine.NewRoutineUiState
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RutinifyNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = composeRule.activity.getString(id)

    @Before
    fun setUp() {
        composeRule.setContent {
            RutinifyTheme {
                // Stateless screen: hiltViewModel() needs a Hilt activity (D-43)
                RutinifyNavHost(
                    newRoutineContent = { onBack, _ -> NewRoutineScreen(NewRoutineUiState(), onAction = {}, onBack = onBack) },
                )
            }
        }
    }

    @Test
    fun `arranca en Inicio con el boton flotante Nueva rutina`() { // R-8.4
        composeRule.onNodeWithText(str(R.string.home_placeholder)).assertExists()
        composeRule.onNodeWithText(str(R.string.action_new_routine), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `el boton flotante abre Nueva rutina y la flecha vuelve a Inicio`() {
        composeRule.onNodeWithText(str(R.string.action_new_routine), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(str(R.string.new_routine_json_label)).assertExists()

        composeRule.onNodeWithContentDescription(str(R.string.action_back)).performClick()

        composeRule.onNodeWithText(str(R.string.home_placeholder)).assertExists()
        composeRule.onNodeWithText(str(R.string.new_routine_json_label)).assertDoesNotExist()
    }

    @Test
    fun `el boton atras del sistema vuelve de Nueva rutina a Inicio`() {
        composeRule.onNodeWithText(str(R.string.action_new_routine), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(str(R.string.new_routine_json_label)).assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(str(R.string.home_placeholder)).assertExists()
    }
}
