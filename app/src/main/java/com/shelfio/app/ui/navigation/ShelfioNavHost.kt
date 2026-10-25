package com.shelfio.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shelfio.app.ui.screens.AboutScreen
import com.shelfio.app.ui.screens.WelcomeScreen

object Routes {
    const val WELCOME = "welcome"
    const val ABOUT = "about"
}

@Composable
fun ShelfioNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onAboutClick = { navController.navigate(Routes.ABOUT) })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
