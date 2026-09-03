package com.cenango.fetchcicg.util

import com.cenango.fetchcicg.data.model.responses.ErrorResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/**
 * Kotlin equivalent of iOS's `Result<T, API.Error>` (API+Error.swift). Wrap
 * Retrofit calls with [safeApiCall] to get this instead of a raw exception.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val error: ApiError) : ApiResult<Nothing>()
}

sealed class ApiError(val message: String) {
    object TokenExpired : ApiError("Token Expired. Please Log Back In.")
    object Unauthorized : ApiError("You don't have authorization to perform this action")
    class Conflict(message: String) : ApiError(message)
    class HttpRequestError(message: String) : ApiError(message)
    class ResponseParseError(message: String) : ApiError(message)
    class ServerError(message: String) : ApiError(message)
    object Unknown : ApiError("Unknown Error Occurred. Please Try Again.")
}

private val gson = Gson()

suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        ApiResult.Error(
            when (e.code()) {
                401 -> ApiError.TokenExpired
                403 -> ApiError.Unauthorized
                409 -> ApiError.Conflict(parseErrorMessage(body))
                else -> ApiError.ServerError(parseErrorMessage(body))
            }
        )
    } catch (e: IOException) {
        ApiResult.Error(ApiError.HttpRequestError(e.message ?: "Network error"))
    } catch (e: Exception) {
        ApiResult.Error(ApiError.ResponseParseError(e.message ?: "Failed to parse response"))
    }
}

private fun parseErrorMessage(body: String?): String {
    if (body.isNullOrBlank()) return ApiError.Unknown.message
    return runCatching { gson.fromJson(body, ErrorResponse::class.java).message }
        .getOrDefault(ApiError.Unknown.message)
}
