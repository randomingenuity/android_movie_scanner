package com.movie.scanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.movie.scanner.ui.list.ListScreen
import com.movie.scanner.ui.loading.LoadingScreen
import com.movie.scanner.ui.review.ReviewScreen
import com.movie.scanner.ui.scan.ScanScreen
import com.movie.scanner.ui.scanbulk.ScanBulkCaptureScreen
import com.movie.scanner.ui.scanbulk.ScanBulkQueueScreen
import com.movie.scanner.ui.settings.SettingsScreen

@Composable
fun MovieScannerNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomDestinations = listOf(
        AppDestination.Scan,
        AppDestination.ScanBulkCapture,
        AppDestination.List,
        AppDestination.Settings,
    )
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val label = when (destination) {
                            AppDestination.Scan -> "Scan"
                            AppDestination.ScanBulkCapture -> "Scan Bulk"
                            AppDestination.List -> "List"
                            AppDestination.Settings -> "Settings"
                            else -> destination.route
                        }
                        val icon = when (destination) {
                            AppDestination.Scan -> Icons.Default.PhotoCamera
                            AppDestination.ScanBulkCapture -> Icons.Default.Collections
                            AppDestination.List -> Icons.Default.List
                            AppDestination.Settings -> Icons.Default.Settings
                            else -> Icons.Default.List
                        }
                        NavigationBarItem(
                            selected = currentRoute == destination.route ||
                                (destination == AppDestination.ScanBulkCapture &&
                                    currentRoute == AppDestination.ScanBulkQueue.route),
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Scan.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Scan.route) {
                ScanScreen(
                    onNavigateToLoading = {
                        navController.navigate(AppDestination.Loading.route)
                    },
                )
            }
            composable(AppDestination.ScanBulkCapture.route) {
                ScanBulkCaptureScreen(
                    onNavigateToQueue = {
                        navController.navigate(AppDestination.ScanBulkQueue.route)
                    },
                )
            }
            composable(AppDestination.ScanBulkQueue.route) {
                ScanBulkQueueScreen(
                    onNavigateToLoading = {
                        navController.navigate(AppDestination.Loading.route)
                    },
                    onNavigateToCapture = {
                        navController.popBackStack(AppDestination.ScanBulkCapture.route, false)
                    },
                    onNavigateToScan = {
                        navController.navigate(AppDestination.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(AppDestination.Loading.route) {
                LoadingScreen(
                    onNavigateToReview = {
                        navController.navigate(AppDestination.Review.route) {
                            popUpTo(AppDestination.Loading.route) {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateBackToCamera = {
                        val returnedToBulkCapture = navController.popBackStack(
                            AppDestination.ScanBulkCapture.route,
                            false,
                        )
                        if (!returnedToBulkCapture) {
                            navController.popBackStack(AppDestination.Scan.route, false)
                        }
                    },
                )
            }
            composable(AppDestination.Review.route) {
                ReviewScreen(
                    onFinished = { isBulkProcessing ->
                        if (isBulkProcessing) {
                            navController.popBackStack(AppDestination.ScanBulkQueue.route, false)
                        } else {
                            navController.navigate(AppDestination.Scan.route) {
                                popUpTo(AppDestination.Scan.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable(AppDestination.List.route) {
                ListScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    onConfigured = {
                        navController.navigate(AppDestination.Scan.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}
