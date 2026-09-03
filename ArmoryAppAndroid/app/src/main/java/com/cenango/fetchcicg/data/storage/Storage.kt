package com.cenango.fetchcicg.data.storage

import android.content.Context
import com.cenango.fetchcicg.data.model.Category
import com.cenango.fetchcicg.data.model.TagFormat
import com.cenango.fetchcicg.data.model.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Kotlin equivalent of iOS's `Storage` (Utils/Storage.swift) — a local cache
 * for reference data fetched once on Dashboard load (tag format, categories,
 * users), as opposed to [com.cenango.fetchcicg.data.session.SessionManager]
 * which holds auth/session state.
 */
class Storage(context: Context) {

    private object Key {
        const val TAG_FORMAT = "tag_format"
        const val CATEGORIES = "categories"
        const val USERS = "users"
    }

    private val prefs = context.getSharedPreferences("armory_storage", Context.MODE_PRIVATE)
    private val gson = Gson()

    var tagFormat: TagFormat?
        get() = prefs.getString(Key.TAG_FORMAT, null)?.let {
            runCatching { gson.fromJson(it, TagFormat::class.java) }.getOrNull()
        }
        set(value) {
            if (value == null) prefs.edit().remove(Key.TAG_FORMAT).apply()
            else prefs.edit().putString(Key.TAG_FORMAT, gson.toJson(value)).apply()
        }

    var categories: List<Category>
        get() = prefs.getString(Key.CATEGORIES, null)?.let {
            runCatching { gson.fromJson<List<Category>>(it, object : TypeToken<List<Category>>() {}.type) }
                .getOrNull()
        } ?: emptyList()
        set(value) = prefs.edit().putString(Key.CATEGORIES, gson.toJson(value)).apply()

    var users: List<User>
        get() = prefs.getString(Key.USERS, null)?.let {
            runCatching { gson.fromJson<List<User>>(it, object : TypeToken<List<User>>() {}.type) }
                .getOrNull()
        } ?: emptyList()
        set(value) = prefs.edit().putString(Key.USERS, gson.toJson(value)).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
