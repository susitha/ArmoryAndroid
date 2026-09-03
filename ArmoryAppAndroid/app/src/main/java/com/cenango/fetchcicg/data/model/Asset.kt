package com.cenango.fetchcicg.data.model

import com.google.gson.annotations.SerializedName

data class Asset(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("serial_number") val serialNumber: String,
    @SerializedName("rfid_tag") val tag: String?,
    val category: Category,
    @SerializedName("assigned_user_id") val assignedUserID: Int?
)
