package com.cenango.fetchcicg.data.network

import com.cenango.fetchcicg.BuildConfig
import com.cenango.fetchcicg.data.session.SessionManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Kotlin equivalent of iOS's `API` + `FetchCICGAPI.buildURL` (API.swift /
 * API+Blueprint.swift). Debug builds hit [ApiSettings.defaultBaseUrl] directly;
 * release builds resolve the real host at runtime from [SessionManager]
 * (populated by the api-details discovery call) and rewrite each request's
 * host on the fly, since Retrofit's own base URL is fixed at client-creation
 * time.
 */
class RetrofitClient(private val sessionManager: SessionManager) {

    companion object {
        private const val PLACEHOLDER_HOST = "armory.internal"
        private const val PLACEHOLDER_BASE_URL = "https://$PLACEHOLDER_HOST/"
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    /** Rewrites the placeholder host to the real, session-resolved API host.
     *  Left alone in debug builds and for absolute-URL calls (e.g. getApiDetails). */
    private val dynamicBaseUrlInterceptor = Interceptor { chain ->
        val original = chain.request()
        if (BuildConfig.DEBUG || original.url.host != PLACEHOLDER_HOST) {
            return@Interceptor chain.proceed(original)
        }

        val resolved = resolvedBaseUrl().toHttpUrl()
        val rewritten = original.url.newBuilder()
            .scheme(resolved.scheme)
            .host(resolved.host)
            .port(resolved.port)
            .build()

        chain.proceed(original.newBuilder().url(rewritten).build())
    }

    /** Mirrors `FetchCICGAPI.buildURL`'s version check: only trust the cached
     *  server URL if it was reported under the API version this app expects. */
    private fun resolvedBaseUrl(): String {
        return if (sessionManager.apiVersion == ApiSettings.API_VERSION) {
            sessionManager.serverUrl ?: sessionManager.defaultUrl ?: ApiSettings.defaultBaseUrl
        } else {
            sessionManager.defaultUrl ?: ApiSettings.defaultBaseUrl
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessionManager))
        .addInterceptor(dynamicBaseUrlInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(TokenAuthenticator(sessionManager))
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(if (BuildConfig.DEBUG) ApiSettings.defaultBaseUrl else PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ArmoryApiService by lazy { retrofit.create(ArmoryApiService::class.java) }
}
