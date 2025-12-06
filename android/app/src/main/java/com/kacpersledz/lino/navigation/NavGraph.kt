package com.kacpersledz.lino.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kacpersledz.lino.ui.controller.ControllerScreen
import com.kacpersledz.lino.ui.scanner.ScannerScreen

sealed class Screen(val route: String) {
    object Scanner : Screen("scanner")
    object Controller : Screen("controller/{deviceAddress}") {
        fun createRoute(deviceAddress: String) = "controller/$deviceAddress"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Scanner.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onDeviceSelected = { deviceAddress ->
                    navController.navigate(Screen.Controller.createRoute(deviceAddress))
                }
            )
        }

        composable(Screen.Controller.route) { backStackEntry ->
            val deviceAddress = backStackEntry.arguments?.getString("deviceAddress") ?: ""
            ControllerScreen(
                deviceAddress = deviceAddress,
                onDisconnect = {
                    navController.popBackStack()
                }
            )
        }
    }
}
