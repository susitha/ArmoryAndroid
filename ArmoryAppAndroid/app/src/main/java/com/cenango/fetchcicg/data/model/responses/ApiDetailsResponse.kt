package com.cenango.fetchcicg.data.model.responses

import com.google.gson.annotations.SerializedName

// Fetched from the fixed "config discovery" host (see ApiSettings.API_DETAILS_URL),
// used to resolve which real API host/version the app should talk to.
data class ApiDetailsResponse(
    val version: Int,
    @SerializedName("endpoint") val url: String,
    @SerializedName("default") val defaultURL: String
)
