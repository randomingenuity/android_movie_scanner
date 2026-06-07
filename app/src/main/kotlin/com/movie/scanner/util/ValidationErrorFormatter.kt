package com.movie.scanner.util

import retrofit2.HttpException

object ValidationErrorFormatter {
    fun format(throwable: Throwable): String {
        if (throwable is HttpException) {
            val responseBody = throwable.response()?.errorBody()?.string()
            if (!responseBody.isNullOrBlank()) {
                return "HTTP ${throwable.code()}: $responseBody"
            }
            return "HTTP ${throwable.code()}: ${throwable.message()}"
        }
        val message = throwable.message
        if (!message.isNullOrBlank()) {
            return message
        }
        return throwable.toString()
    }
}
