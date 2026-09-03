package com.cenango.fetchcicg.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Int,
    @SerializedName("identity_number") val identityNumber: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val username: String,
    val email: String?,
    val phone: String?,
    @SerializedName("userGroup") val userGroup: UserGroup
) {
    val fullName: String
        get() = "$firstName $lastName"
}
