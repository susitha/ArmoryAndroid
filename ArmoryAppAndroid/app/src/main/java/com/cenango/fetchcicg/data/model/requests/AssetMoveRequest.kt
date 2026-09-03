package com.cenango.fetchcicg.data.model.requests

import com.google.gson.annotations.SerializedName

data class AssetMoveRequest(
    @SerializedName("user_id") val userID: Int,
    val password: String,
    @SerializedName("no_of_magazine") val magazinesCount: Int?,
    @SerializedName("total_weight") val totalWeight: Double?,
    @SerializedName("discrepancy_note") val note: String?
)
