package com.cenango.fetchcicg.data.model.responses

data class ErrorMessage(
    val message: String
)

data class ErrorResponse(
    val errors: List<ErrorMessage>
) {
    val message: String
        get() = errors.joinToString("\n") { it.message }
}
