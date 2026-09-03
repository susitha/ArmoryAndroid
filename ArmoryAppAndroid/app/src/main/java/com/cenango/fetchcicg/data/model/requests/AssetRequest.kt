package com.cenango.fetchcicg.data.model.requests

import com.google.gson.annotations.SerializedName

data class AssetRequest(
    val name: String,
    val description: String?,
    @SerializedName("category_id") val categoryID: Int,
    @SerializedName("rfid_tag") val tag: String,
    @SerializedName("serial_number") val serialNumber: String,
    @SerializedName("assigned_user_id") val assignedUserID: Int?
)
