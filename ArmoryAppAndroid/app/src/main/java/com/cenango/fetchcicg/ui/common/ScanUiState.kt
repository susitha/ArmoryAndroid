package com.cenango.fetchcicg.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cenango.fetchcicg.R

/**
 * Kotlin equivalent of iOS's `ScanState` (Utils/Globals.swift) — drives the
 * text/subtitle/icon/color shown on the scan screen. None of the icons reuse
 * iOS's `device_connected`/`device_disconnected`/`device_battery`/
 * `device_overheated` illustrations — those are literal photos of the old
 * AsReader Bluetooth gun accessory, which doesn't exist on the Chainway C72
 * (a single integrated handheld) — reusing them would show the wrong
 * hardware. `ic_status_*` are hand-built two-tone (colored circle + white
 * glyph) icons matching the same visual language as the ported `scan`/
 * `success`/`failed` illustrations, with color baked into the icon itself.
 *
 * `colorRes` mirrors `ScanState.color` and is applied to the title text —
 * safe now that the card is white (confirmed against a real screenshot);
 * an earlier pass fixed the title to plain white because a *different*
 * screenshot suggested a blue card, where blue-on-blue for the "scanning"
 * state would have been invisible. See SCAFFOLD.md.
 */
enum class ScanUiState(
    @StringRes val textRes: Int,
    @StringRes val infoRes: Int?,
    @DrawableRes val iconRes: Int,
    val colorRes: Int
) {
    DEVICE_CONNECTED(R.string.scan_state_connected, null, R.drawable.ic_status_connected, R.color.status_connected),
    DEVICE_NOT_CONNECTED(R.string.scan_state_not_connected, R.string.scan_state_not_connected_info, R.drawable.ic_status_not_connected, R.color.status_disconnected),
    LOW_BATTERY(R.string.scan_state_low_battery, R.string.scan_state_low_battery_info, R.drawable.ic_status_low_battery, R.color.status_low_battery),
    OVERHEATED(R.string.scan_state_overheated, R.string.scan_state_overheated_info, R.drawable.ic_status_overheated, R.color.status_error),
    SCANNING(R.string.scan_state_scanning, R.string.scan_state_scanning_info, R.drawable.scan_frame, R.color.primary),
    SUCCESS(R.string.scan_state_connected, null, R.drawable.status_success, R.color.status_connected),
    FAILED(R.string.scan_state_failed, R.string.scan_state_failed_info, R.drawable.status_failed, R.color.status_error)
}
