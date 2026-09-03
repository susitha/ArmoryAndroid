package com.cenango.fetchcicg.data.model

enum class InventoryGroup(val value: String) {
    AVAILABLE("in_location"),
    MISSING("not_in_location"),
    CHECKED_OUT("checkouts")
}
