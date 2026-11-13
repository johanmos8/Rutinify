package com.mirkwood.rutinify.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.home.HomeScreen
import com.mirkwood.rutinify.ui.home.HomeUiState
import com.mirkwood.rutinify.ui.preview.PreviewData
import com.mirkwood.rutinify.ui.newroutine.NewRoutineScreen
import com.mirkwood.rutinify.ui.newroutine.NewRoutineUiState
import com.mirkwood.rutinify.ui.orderdetail.OrderDetailScreen
import com.mirkwood.rutinify.ui.orderdetail.OrderDetailUiState
import com.google.common.truth.Truth.assertThat
import com.mirkwood.rutinify.ui.theme.RutinifyTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RutinifyNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Home state shown by the stateless seam; a test can switch it to a list of orders. */
    private val homeState = mutableStateOf(HomeUiState(isLoading = false))

    private lateinit var navController: NavHostController

    private fun str(id: Int) = composeRule.activity.getString(id)

    @Before
    fun setUp() {
        composeRule.setContent {
            RutinifyTheme {
                navController = rememberNavController()
                // Stateless screen: hiltViewModel() needs a Hilt activity (D-43)
                RutinifyNavHost(
                    navController = navController,
                    homeContent = { onNew, onOrder ->
                        HomeScreen(homeState.value, onNewRoutineClick = onNew, onOrderClick = onOrder)
                    },
                    newRoutineContent = { onBack, _ -> NewRoutineScreen(NewRoutineUiState(), onAction = {}, onBack = onBack) },
                    orderDetailContent = { onBack -> OrderDetailScreen(OrderDetailUiState(isLoading = false, notFound = true), onAction = {}, onBack = onBack) },
                )
            }
        }
    }

    @Test
    fun `arranca en Inicio con el estado vacio y el boton flotante Nueva rutina`() { // R-8.4, R-8.5
        composeRule.onNodeWithText(str(R.string.home_empty_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.action_new_routine), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `el boton flotante abre Nueva rutina y la flecha vuelve a Inicio`() {
        composeRule.onNodeWithText(str(R.string.action_new_routine), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(str(R.string.new_routine_json_label)).assertExists()

        composeRule.onNodeWithContentDescription(str(R.string.action_back)).performClick()

        composeRule.onNodeWithText(str(R.string.home_empty_title)).assertExists()
        composeRule.onNodeWithText(str(R.string.new_routine_json_label)).assertDoesNotExist()
    }

    @Test
    fun `el boton atras del sistema vuelve de Nueva rutina a Inicio`() {
        composeRule.onNodeWithText(str(R.string.action_new_routine), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(str(R.string.new_routine_json_label)).assertExists()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(str(R.string.home_empty_title)).assertExists()
    }

    @Test
    fun `tocar una orden abre su detalle y atras vuelve a Inicio`() { // R-8.2, design §10
        composeRule.runOnUiThread { homeState.value = PreviewData.homeState }
        composeRule.onNodeWithText("Cachorro").performClick()

        composeRule.onNodeWithText(str(R.string.order_detail_title)).assertExists()

        composeRule.onNodeWithContentDescription(str(R.string.action_back)).performClick()

        composeRule.onNodeWithText("Cachorro").assertExists()
    }

    @Test
    fun `el enlace de la notificacion abre el detalle con la alarma resaltada`() { // R-7.6
        composeRule.runOnUiThread { navController.navigate("rutinify://order/5?alarm=9".toUri()) }
        composeRule.waitForIdle()

        val route = composeRule.runOnUiThread { navController.currentBackStackEntry?.toRoute<OrderDetailDestination>() }
        assertThat(route).isEqualTo(OrderDetailDestination(orderId = 5, highlightAlarmId = 9))
        composeRule.onNodeWithText(str(R.string.order_detail_not_found)).assertExists()
    }
}
