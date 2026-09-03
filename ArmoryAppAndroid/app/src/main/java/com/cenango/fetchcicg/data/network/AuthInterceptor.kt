package com.cenango.fetchcicg.data.network

import com.cenango.fetchcicg.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Mirrors the `headers` switch in iOS's `FetchCICGAPI` (API+Blueprint.swift):
 * every endpoint except login/getApiDetails sends the raw token (no "Bearer "
 * prefix — that's how the backend expects it) as the `Authorization` header.
 *
 * Endpoints that must skip auth (login, api-details discovery) tag their
 * Retrofit method with `@Headers("$NO_AUTH_HEADER: true")`.
 *
 * A method that sets its own `@Header("Authorization")` (e.g.
 * `checkinApproveAssetWithSuperUser`, which needs a one-off admin token
 * instead of the session's) is left untouched — see the early-return below —
 * since Retrofit populates per-call `@Header`s before this interceptor runs.
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    companion object {
        const val NO_AUTH_HEADER = "No-Authentication"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        if (original.header(NO_AUTH_HEADER) != null) {
            val stripped = original.newBuilder().removeHeader(NO_AUTH_HEADER).build()
            return chain.proceed(stripped)
        }

        if (original.header("Authorization") != null) {
            return chain.proceed(original)
        }

        val authorized = original.newBuilder()
            .header("Authorization", sessionManager.token.orEmpty())
            .build()
        return chain.proceed(authorized)
    }
}
