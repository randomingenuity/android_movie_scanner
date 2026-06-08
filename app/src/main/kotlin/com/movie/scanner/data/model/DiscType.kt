package com.movie.scanner.data.model

object DiscType {
    const val BLU_RAY = "Blu-Ray"
    const val DVD = "DVD"
    const val BLU_RAY_4K = "4K Blu-Ray"
    const val BLU_RAY_3D = "3D Blu-Ray"
    const val HD_DVD = "HD DVD"

    val options = listOf(
        BLU_RAY,
        DVD,
        BLU_RAY_4K,
        BLU_RAY_3D,
        HD_DVD,
    )
}
