package com.cenango.fetchcicg.data.model.responses

import com.cenango.fetchcicg.data.model.Asset
import com.google.gson.annotations.SerializedName

data class InventoryListResponse(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerializedName("records") val assets: List<Asset>
)
