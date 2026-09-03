package com.cenango.fetchcicg.data.session

import android.content.Context
import android.content.SharedPreferences
import com.cenango.fetchcicg.data.model.User
import com.google.gson.Gson

/**
 * Kotlin equivalent of iOS's `Session` (Utils/Session.swift). Backed by
 * SharedPreferences instead of UserDefaults; same fields, same purpose.
 */
class SessionManager(context: Context) {

    private object Key {
        const val TOKEN = "user_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val USER = "user"
        const val IS_IN_REVIEW = "is_in_review"
        const val SERVER_URL = "server_url"
        const val DEFAULT_URL = "default_url"
        const val API_VERSION = "api_version"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("armory_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    var serverUrl: String?
        get() = prefs.getString(Key.SERVER_URL, null)
        set(value) = prefs.edit().putString(Key.SERVER_URL, value).apply()

    var defaultUrl: String?
        get() = prefs.getString(Key.DEFAULT_URL, null)
        set(value) = prefs.edit().putString(Key.DEFAULT_URL, value).apply()

    var apiVersion: Int?
        get() = if (prefs.contains(Key.API_VERSION)) prefs.getInt(Key.API_VERSION, 0) else null
        set(value) {
            if (value == null) prefs.edit().remove(Key.API_VERSION).apply()
            else prefs.edit().putInt(Key.API_VERSION, value).apply()
        }

    var token: String?
        get() = prefs.getString(Key.TOKEN, null)
        set(value) = prefs.edit().putString(Key.TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(Key.REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(Key.REFRESH_TOKEN, value).apply()

    var user: User?
        get() = prefs.getString(Key.USER, null)?.let {
            runCatching { gson.fromJson(it, User::class.java) }.getOrNull()
        }
        set(value) {
            if (value == null) prefs.edit().remove(Key.USER).apply()
            else prefs.edit().putString(Key.USER, gson.toJson(value)).apply()
        }

    var isInReview: Boolean
        get() = prefs.getBoolean(Key.IS_IN_REVIEW, false)
        set(value) = prefs.edit().putBoolean(Key.IS_IN_REVIEW, value).apply()

    fun clearSession() {
        token = null
        refreshToken = null
        user = null
        isInReview = false
    }
}
