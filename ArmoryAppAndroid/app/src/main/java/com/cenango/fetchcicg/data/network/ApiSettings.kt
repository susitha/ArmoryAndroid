package com.cenango.fetchcicg.data.network

import com.cenango.fetchcicg.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Kotlin equivalent of iOS's `ApiSettings` + the `#if DEBUG` branch inside
 * `FetchCICGAPI.buildURL` (API+Blueprint.swift).
 */
object ApiSettings {
    /** Bump when the request/response contract changes; compared against the
     *  server's reported version from [API_DETAILS_URL] to decide whether to
     *  trust `Session.serverUrl` or fall back to `Session.defaultUrl`. */
    const val API_VERSION: Int = 10

    /** Fixed "config discovery" endpoint — same host iOS hits for `getAPIDetails`. */
    const val API_DETAILS_HOST: String = "http://52.2.232.129"
    const val API_DETAILS_PATH: String = "/cicg_config/index.json"

    /** Debug builds talk to the dev API directly; release builds resolve the
     *  real host from [API_DETAILS_HOST] and cache it via SessionManager. */
    val defaultBaseUrl: String
        get() = BuildConfig.API_BASE_URL

    /** Mirrors iOS's `buildAPIDetailsURL`: same host/path plus a cache-busting
     *  timestamp query param in the same `yyyyMMddHHmm` format. */
    fun apiDetailsUrl(): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return "$API_DETAILS_HOST$API_DETAILS_PATH?t=${formatter.format(Date())}"
    }
}
