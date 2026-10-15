package com.movie.scanner.data.model

/**
 * Movie edition variants. [value] is stored in the database and exported as `movie_release_type`;
 * [label] is shown in pickers and list detail overlays. [NO_SPECIFIC_EDITION] uses an empty [value].
 */
enum class MovieEdition(val value: String, val label: String) {
    THEATRICAL("theatrical", "Theatrical"),
    DIRECTORS_CUT("directors_cut", "Director's Cut"),
    EXTENDED_VERSION("extended_version", "Extended Version"),
    UNRATED("unrated", "Unrated"),
    DELUXE_EDITION("deluxe_edition", "Deluxe Edition"),
    SPECIAL_EDITION("special_edition", "Special Edition"),
    WITH_BONUS_MATERIAL("with_bonus_material", "With Bonus Material"),
    RESTORED("restored", "Restored"),
    STEELBOX_EDITION("steelbox_edition", "Steelbox Edition"),
    NO_SPECIFIC_EDITION("", "No specific edition"),
    ;

    companion object {
        val options: List<MovieEdition> = entries.sortedBy { edition -> edition.label }

        fun fromValue(value: String): MovieEdition? =
            entries.firstOrNull { edition -> edition.value == value }

        fun fromLabel(label: String): MovieEdition? =
            entries.firstOrNull { edition -> edition.label == label }

        fun fromStored(stored: String): MovieEdition? =
            if (stored.isEmpty()) {
                NO_SPECIFIC_EDITION
            } else {
                fromValue(stored) ?: fromLabel(stored)
            }

        fun labelForStored(stored: String?): String =
            when {
                stored == null -> ""
                stored.isEmpty() -> NO_SPECIFIC_EDITION.label
                else -> fromStored(stored)?.label ?: stored
            }
    }
}
