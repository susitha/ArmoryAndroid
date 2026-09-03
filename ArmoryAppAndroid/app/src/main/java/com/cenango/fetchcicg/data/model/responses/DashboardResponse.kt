package com.cenango.fetchcicg.data.model.responses

import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    @SerializedName("total_checkouts") val totalCheckouts: Int,
    @SerializedName("today_checkouts") val todayCheckouts: Int,
    @SerializedName("total_assets") val totalAssets: Int,
    @SerializedName("not_in_location") val totalMissing: Int?
)
