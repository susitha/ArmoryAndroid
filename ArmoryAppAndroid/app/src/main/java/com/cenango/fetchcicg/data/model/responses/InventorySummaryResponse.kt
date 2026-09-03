package com.cenango.fetchcicg.data.model.responses

import com.google.gson.annotations.SerializedName

data class InventorySummaryResponse(
    @SerializedName("in_location") val available: Int,
    @SerializedName("not_in_location") val missing: Int,
    @SerializedName("checkouts") val checkedOut: Int
)
