package com.movie.scanner.util

object IntegerInput {
    fun filterDigits(value: String): String =
        value.filter { character -> character.isDigit() }

    fun parseOptionalInt(value: String): Int? =
        value.takeIf { text -> text.isNotBlank() }?.toIntOrNull()
}
