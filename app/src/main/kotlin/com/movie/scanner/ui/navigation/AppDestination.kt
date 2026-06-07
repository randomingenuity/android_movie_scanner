package com.movie.scanner.ui.navigation

sealed class AppDestination(val route: String) {
    data object Scan : AppDestination("scan")

    data object Loading : AppDestination("loading")

    data object Review : AppDestination("review")

    data object List : AppDestination("list")

    data object Settings : AppDestination("settings")
}
