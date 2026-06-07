package com.movie.scanner.data.model

enum class FieldValidationStatus {
    IDLE,
    VALIDATING,
    VALID,
    INVALID,
}

data class ValidatedField(
    val status: FieldValidationStatus = FieldValidationStatus.IDLE,
    val errorMessage: String? = null,
)

data class TmdbLanguageOption(
    val code: String,
    val displayName: String,
)
