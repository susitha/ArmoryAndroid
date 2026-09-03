package com.cenango.fetchcicg.ui.common

import android.graphics.Color
import com.cenango.fetchcicg.R

/**
 * Kotlin equivalent of the `switch asset.category.name.lowercased()` in
 * iOS's `AssetDetailsView.set(asset:)` (Views/Assets/AssetDetailsView.swift),
 * which picks an icon + tint color per category. That switch only covers
 * "hand gun"/"rifle"/"machine gun" (falling back to the handgun icon for
 * anything else); [iconFor] mirrors that with the real ported weapon icons,
 * but returns null for an unrecognized category instead of iOS's hardcoded
 * handgun fallback — callers fall back to [initialFor]'s letter-in-circle
 * instead, which handles unknown category names more gracefully.
 */
object AssetCategoryVisuals {
    fun colorFor(categoryName: String): Int = when (categoryName.trim().lowercase()) {
        "hand gun" -> Color.parseColor("#34C759")
        "rifle" -> Color.parseColor("#FF9500")
        "machine gun" -> Color.parseColor("#FF3B30")
        else -> Color.parseColor("#34C759")
    }

    fun iconFor(categoryName: String): Int? = when (categoryName.trim().lowercase()) {
        "hand gun" -> R.drawable.handgun
        "rifle" -> R.drawable.rifle
        "machine gun" -> R.drawable.machinegun
        else -> null
    }

    fun initialFor(categoryName: String): String =
        categoryName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}
