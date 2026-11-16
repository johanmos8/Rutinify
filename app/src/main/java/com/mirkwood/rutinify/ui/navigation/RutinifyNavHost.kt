package com.mirkwood.rutinify.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.mirkwood.rutinify.ui.home.HomeRoute
import com.mirkwood.rutinify.ui.newroutine.NewRoutineRoute
import com.mirkwood.rutinify.ui.orderdetail.OrderDetailRoute
import com.mirkwood.rutinify.ui.permissions.PermissionsRoute
import com.mirkwood.rutinify.ui.review.ReviewRoute

/**
 * App navigation graph (design §10). [homeContent], [newRoutineContent], [orderDetailContent] and [permissionsContent] are
 * seams so JVM navigation tests can show the stateless screens instead of the Hilt-backed routes (D-43).
 */
@Composable
fun RutinifyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    homeContent: @Composable (onNewRoutineClick: () -> Unit, onOrderClick: (Long) -> Unit, onPermissionsClick: () -> Unit) -> Unit =
        { onNewRoutineClick, onOrderClick, onPermissionsClick ->
            HomeRoute(onNewRoutineClick = onNewRoutineClick, onOrderClick = onOrderClick, onPermissionsClick = onPermissionsClick)
        },
    newRoutineContent: @Composable (onBack: () -> Unit, onNavigateToReview: (String) -> Unit) -> Unit =
        { onBack, onNavigateToReview -> NewRoutineRoute(onBack = onBack, onNavigateToReview = onNavigateToReview) },
    permissionsContent: @Composable (onBack: () -> Unit) -> Unit = { onBack -> PermissionsRoute(onBack = onBack) },
    orderDetailContent: @Composable (onBack: () -> Unit, onNavigateToReview: (String) -> Unit) -> Unit =
        { onBack, onNavigateToReview -> OrderDetailRoute(onBack = onBack, onNavigateToReview = onNavigateToReview) },
) {
    val navigateUp: () -> Unit = { navController.navigateUp() }

    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = modifier,
    ) {
        composable<HomeDestination> {
            homeContent(
                { navController.navigate(NewRoutineDestination()) },
                { orderId -> navController.navigate(OrderDetailDestination(orderId)) },
                { navController.navigate(PermissionsDestination) },
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
        // Notification body (R-7.6); the PendingIntent is explicit, so no intent filter is needed
        composable<OrderDetailDestination>(deepLinks = listOf(navDeepLink { uriPattern = ORDER_DEEP_LINK })) {
            // "Duplicar para otra fecha" (R-10.7): Review then replaces the stack up to Home
            orderDetailContent(navigateUp) { draftId -> navController.navigate(ReviewDestination(draftId)) }
        }
        composable<PermissionsDestination> {
            permissionsContent(navigateUp)
        }
    }
}
