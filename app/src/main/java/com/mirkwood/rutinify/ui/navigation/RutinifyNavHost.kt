package com.mirkwood.rutinify.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.PlaceholderScreen
import com.mirkwood.rutinify.ui.home.HomeRoute
import com.mirkwood.rutinify.ui.newroutine.NewRoutineRoute
import com.mirkwood.rutinify.ui.review.ReviewRoute

/**
 * App navigation graph (design §10). [homeContent] and [newRoutineContent] are seams so JVM navigation
 * tests can show the stateless screens instead of the Hilt-backed routes (D-43).
 */
@Composable
fun RutinifyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    homeContent: @Composable (onNewRoutineClick: () -> Unit, onOrderClick: (Long) -> Unit) -> Unit =
        { onNewRoutineClick, onOrderClick -> HomeRoute(onNewRoutineClick = onNewRoutineClick, onOrderClick = onOrderClick) },
    newRoutineContent: @Composable (onBack: () -> Unit, onNavigateToReview: (String) -> Unit) -> Unit =
        { onBack, onNavigateToReview -> NewRoutineRoute(onBack = onBack, onNavigateToReview = onNavigateToReview) },
) {
    val navigateUp: () -> Unit = { navController.navigateUp() }

    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = modifier,
    ) {
        composable<HomeDestination> {
            homeContent(
                { navController.navigate(NewRoutineDestination) },
                { orderId -> navController.navigate(OrderDetailDestination(orderId)) },
            )
        }
        composable<NewRoutineDestination> {
            newRoutineContent(navigateUp) { draftId -> navController.navigate(ReviewDestination(draftId)) }
        }
        composable<ReviewDestination> {
            ReviewRoute(
                onBack = navigateUp,
                onNavigateToOrder = { orderId, count ->
                    navController.navigate(OrderDetailDestination(orderId, scheduledCount = count)) {
                        popUpTo(HomeDestination) // Back from the new order goes Home (design §10)
                    }
                },
            )
        }
        // Placeholders: replaced in T-24 and T-29
        composable<OrderDetailDestination> {
            PlaceholderScreen(titleRes = R.string.order_detail_title, onBack = navigateUp)
        }
        composable<PermissionsDestination> {
            PlaceholderScreen(titleRes = R.string.permissions_title, onBack = navigateUp)
        }
    }
}
