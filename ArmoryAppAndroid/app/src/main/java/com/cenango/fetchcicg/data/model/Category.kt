package com.cenango.fetchcicg.data.model

import com.google.gson.annotations.SerializedName

data class Category(
    val id: Int,
    val name: String,
    val description: String?,
    @SerializedName("is_weight_required") val isWeightRequired: Boolean,
    @SerializedName("is_admin_privilege_required_to_checkout") val isAdminRequiredToCheckout: Boolean,
    @SerializedName("number_of_assets_for_user") val numberOfAssetsPerUser: Int?,
    @SerializedName("childCategories") val subCategories: List<Category>?,
    @SerializedName("is_personal_weapon") val isPersonalWeapon: Boolean
)
