package com.cenango.fetchcicg.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.data.model.Asset
import com.cenango.fetchcicg.ui.common.bindAssetHeader

/**
 * Kotlin/Android equivalent of iOS's `AssetCell` (Views/Cells/AssetCell.swift).
 * Reuses `view_asset_header.xml`/`bindAssetHeader` — same icon/name/category/
 * serial content as the Checkout/Checkin asset header, just as a list row
 * here instead of a one-off card.
 */
class AssetListAdapter(
    private val onAssetClick: (Asset) -> Unit
) : RecyclerView.Adapter<AssetListAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView)

    private val assets = mutableListOf<Asset>()

    fun submitList(newAssets: List<Asset>) {
        assets.clear()
        assets.addAll(newAssets)
        notifyDataSetChanged()
    }

    fun appendList(moreAssets: List<Asset>) {
        val startIndex = assets.size
        assets.addAll(moreAssets)
        notifyItemRangeInserted(startIndex, moreAssets.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_asset_header, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val asset = assets[position]
        bindAssetHeader(holder.itemView, asset)
        holder.itemView.setOnClickListener { onAssetClick(asset) }
    }

    override fun getItemCount(): Int = assets.size
}
