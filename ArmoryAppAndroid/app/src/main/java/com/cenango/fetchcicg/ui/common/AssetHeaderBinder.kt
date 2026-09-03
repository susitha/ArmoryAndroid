package com.cenango.fetchcicg.ui.common

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.Asset

/**
 * Binds asset details to a `view_asset_header.xml` instance — the Kotlin
 * equivalent of iOS's `AssetDetailsView.set(asset:)`. Takes primitives
 * rather than a full [Asset] so screens that only carry a few of its fields
 * forward via `Intent` extras (Summary, the Checkin admin re-auth screen)
 * don't need to reconstruct a whole fake `Asset`/`Category` just to redraw
 * this header — see [bindAssetHeader] (the [Asset] overload) for screens
 * that do have the real object on hand.
 */
fun bindAssetHeader(headerView: View, name: String, categoryName: String, serialNumber: String) {
    val iconBackground = headerView.findViewById<View>(R.id.assetIconBackground)
    // .mutate() first — otherwise setColor() would recolor every view still
    // sharing the cached bg_circle drawable instance, not just this one.
    (iconBackground.background.mutate() as GradientDrawable).setColor(AssetCategoryVisuals.colorFor(categoryName))

    val iconImage = headerView.findViewById<ImageView>(R.id.assetIconImage)
    val iconLetter = headerView.findViewById<TextView>(R.id.assetIconLetter)
    val iconRes = AssetCategoryVisuals.iconFor(categoryName)
    if (iconRes != null) {
        iconImage.setImageResource(iconRes)
        iconImage.visibility = View.VISIBLE
        iconLetter.visibility = View.GONE
    } else {
        iconImage.visibility = View.GONE
        iconLetter.visibility = View.VISIBLE
        iconLetter.text = AssetCategoryVisuals.initialFor(categoryName)
    }

    headerView.findViewById<TextView>(R.id.assetName).text = name
    headerView.findViewById<TextView>(R.id.assetCategory).text =
        headerView.context.getString(R.string.asset_header_category_format, categoryName)
    headerView.findViewById<TextView>(R.id.assetSerialNumber).text =
        headerView.context.getString(R.string.asset_header_serial_format, serialNumber)
}

fun bindAssetHeader(headerView: View, asset: Asset) {
    bindAssetHeader(headerView, asset.name, asset.category.name, asset.serialNumber)
}
