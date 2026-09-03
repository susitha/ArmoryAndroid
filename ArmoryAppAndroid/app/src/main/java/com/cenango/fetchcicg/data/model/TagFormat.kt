package com.cenango.fetchcicg.data.model

import com.google.gson.annotations.SerializedName

// Also doubles as the network response shape — the API returns this object
// as-is with no wrapper key (same as iOS's TagFormatResponse).
data class TagFormat(
    @SerializedName("tag_offset") val offset: Int,
    @SerializedName("tag_length") val length: Int,
    @SerializedName("tag_mask_index") val maskIndex: Int,
    @SerializedName("tag_mask_length") val maskLength: Int
)
