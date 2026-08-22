package com.movie.scanner.data.model

/**
 * Canonical disc media types. [value] is stored in the database and written to export files;
 * [label] is shown in pickers and list detail overlays.
 */
enum class DiscType(val value: String, val label: String) {
    BLURAY("bluray", "Blu-Ray"),
    DVD("dvd", "DVD"),
    BLURAY_4K("4k_bluray", "4K Blu-Ray"),
    BLURAY_3D("3d_bluray", "3D Blu-Ray"),
    HD_DVD("hd_dvd", "HD DVD"),
    ;

    companion object {
        val options: List<DiscType> = entries.sortedBy { discType -> discType.label }

        fun fromValue(value: String): DiscType? =
            entries.firstOrNull { discType -> discType.value == value }

        fun fromLabel(label: String): DiscType? =
            entries.firstOrNull { discType -> discType.label == label }

        /**
         * Resolves a stored export value or a legacy display label from older app versions.
         */
        fun fromStored(stored: String): DiscType? =
            fromValue(stored) ?: fromLabel(stored)

        fun labelForStored(stored: String?): String =
            stored?.let { value -> fromStored(value)?.label ?: value }.orEmpty()
    }
}
