package com.cenango.fetchcicg.data.network

import com.cenango.fetchcicg.data.session.SessionManager
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Route

/**
 * Mirrors iOS's `API.refreshToken` (API.swift): on a 401, exchange the stored
 * refresh token for a new access token and retry the original request once.
 *
 * Uses a bare OkHttpClient (not the shared Retrofit instance) to avoid a
 * circular dependency between the authenticator and the client it authenticates.
 */
class TokenAuthenticator(private val sessionManager: SessionManager) : Authenticator {

    private val client = OkHttpClient()
    private val gson = Gson()
    private data class RefreshBody(val refresh_token: String)
    private data class RefreshResult(val token: String, val refresh_token: String)

    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        // Retrofit/OkHttp calls this once per distinct auth challenge; bail out
        // if we've already retried this request to avoid an infinite loop.
        if (responseCount(response) >= 2) return null

        val refreshToken = sessionManager.refreshToken ?: return null

        val newTokens = runCatching { refresh(refreshToken) }.getOrNull() ?: run {
            // Refresh failed — force a logout, same as iOS falling through to
            // `.tokenExpired` with no successful retry.
            sessionManager.clearSession()
            return null
        }

        sessionManager.token = newTokens.token
        sessionManager.refreshToken = newTokens.refresh_token

        return response.request.newBuilder()
            .header("Authorization", newTokens.token)
            .build()
    }

    private fun refresh(refreshToken: String): RefreshResult {
        val baseUrl = sessionManager.serverUrl ?: ApiSettings.defaultBaseUrl
        val body = gson.toJson(RefreshBody(refreshToken))
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/auth/token")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "Refresh token request failed: ${resp.code}" }
            val json = requireNotNull(resp.body).string()
            return gson.fromJson(json, RefreshResult::class.java)
        }
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
