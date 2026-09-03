package com.cenango.fetchcicg.rfid

/**
 * Kotlin equivalent of iOS's `BatteryStatus` (AsReaderGUNManager+Enums.swift).
 * The old AsReader gun only reported a discrete level; the Chainway C72
 * reports an actual percentage, so [fromPercentage] buckets it back into the
 * same five levels the UI already expects — replace call sites with the raw
 * percentage directly once the UI is rebuilt, if finer granularity is wanted.
 */
enum class RfidBatteryStatus(val percentageText: String) {
    NO_DATA("--"),
    NONE("0%"),
    QUARTER("25%"),
    HALF("50%"),
    THIRD_QUARTER("75%"),
    FULL("100%");

    companion object {
        fun fromPercentage(percent: Int?): RfidBatteryStatus = when {
            percent == null -> NO_DATA
            percent <= 0 -> NONE
            percent < 40 -> QUARTER
            percent < 65 -> HALF
            percent < 90 -> THIRD_QUARTER
            else -> FULL
        }
    }
}
