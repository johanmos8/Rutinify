package com.mirkwood.rutinify.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mirkwood.rutinify.R
import com.mirkwood.rutinify.ui.common.PlaceholderScreen
import com.mirkwood.rutinify.ui.home.HomeScreen
import com.mirkwood.rutinify.ui.newroutine.NewRoutineScreen

@Composable
fun RutinifyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val navigateUp: () -> Unit = { navController.navigateUp() }

    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = modifier,
    ) {
        composable<HomeDestination> {
            HomeScreen(onNewRoutineClick = { navController.navigate(NewRoutineDestination) })
        }
        composable<NewRoutineDestination> {
            NewRoutineScreen(onBack = navigateUp)
        }
        // Placeholders: se sustituyen en T-22, T-24 y T-29
        composable<ReviewDestination> {
            PlaceholderScreen(titleRes = R.string.review_title, onBack = navigateUp)
        }
        composable<OrderDetailDestination> {
            PlaceholderScreen(titleRes = R.string.order_detail_title, onBack = navigateUp)
        }
        composable<PermissionsDestination> {
            PlaceholderScreen(titleRes = R.string.permissions_title, onBack = navigateUp)
        }
    }
}
