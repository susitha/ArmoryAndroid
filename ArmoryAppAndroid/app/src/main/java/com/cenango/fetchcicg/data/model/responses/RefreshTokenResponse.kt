package com.cenango.fetchcicg.data.model.responses

import com.google.gson.annotations.SerializedName

data class RefreshTokenResponse(
    val token: String,
    @SerializedName("refresh_token") val refreshToken: String
)
