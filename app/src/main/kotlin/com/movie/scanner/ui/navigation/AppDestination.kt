package com.movie.scanner.ui.navigation

sealed class AppDestination(val route: String) {
    data object Scan : AppDestination("scan")

    data object ScanBulkCapture : AppDestination("scan_bulk_capture")

    data object ScanBulkQueue : AppDestination("scan_bulk_queue")

    data object Loading : AppDestination("loading")

    data object Review : AppDestination("review")

    data object List : AppDestination("list")

    data object Settings : AppDestination("settings")
}
