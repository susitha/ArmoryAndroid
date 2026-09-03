package com.cenango.fetchcicg.data.model.responses

import com.cenango.fetchcicg.data.model.User
import com.google.gson.annotations.SerializedName

data class LoginResponse(
    val token: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: User,
    @SerializedName("is_review_state") val isInReview: Boolean
)
