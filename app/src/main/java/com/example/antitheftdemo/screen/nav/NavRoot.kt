package com.example.antitheftdemo.screen.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.antitheftdemo.screen.home.HomeScreen
import com.example.antitheftdemo.screen.stop.StopScreen

@Composable
fun NavRoot(
    navHostController: NavHostController
) {
    NavHost(
        navController = navHostController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigation = {
                    navHostController.navigate(Screen.Stop.route)
                }
            )
        }
        composable(route = Screen.Stop.route) {
            StopScreen()
        }
    }
}

enum class Screen(val route: String) {
    Home("home"),
    Stop("stop")
}