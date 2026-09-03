package com.cenango.fetchcicg.rfid

/**
 * Parameters for [RfidManager.setMask], mirroring the tuple iOS passes to
 * `AsReaderGUNManager.setMask(forEPC:maskParameters:)`.
 */
data class EpcMaskParameters(
    val startIndex: Int,
    val tagLength: Int,
    val offset: Int,
    val maskLength: Int
)

/**
 * Kotlin equivalent of iOS's `AsReaderGUNManager` (Services/AsReaderManager/AsReaderGUNManager.swift),
 * now backed by the Chainway C72's built-in UHF module instead of the AsReader
 * Bluetooth gun accessory. Every call site that used `AsReaderGUNManager.shared`
 * should port to this interface unchanged — only the concrete implementation
 * ([ChainwayRfidManager]) changes.
 */
interface RfidManager {

    var listener: RfidManagerListener?

    val hasBarcodeSupport: Boolean?
    val hasRfidSupport: Boolean?
    val isDeviceConnected: Boolean

    /** Mirrors the reader's physical trigger key being enabled/disabled in software. */
    var enableTriggerButton: Boolean

    /**
     * Tag mode — used for tagging assets/containers. Continuous scanning is
     * off and power is set to the minimum gain, since only one tag needs to
     * be read at a time.
     */
    fun setTagRfidMode()

    /**
     * Search mode — used for searching/locating assets. Continuous scanning
     * is on, power is set to the maximum gain, and RSSI reporting is enabled
     * so the UI can estimate distance to the tag.
     */
    fun setSearchRfidMode()

    fun startRfidScanning()
    fun stopRfidScanning()

    fun clearMask()

    /** @throws IllegalArgumentException if [parameters] can't carve a valid mask out of [epc]. */
    fun setMask(epc: String, parameters: EpcMaskParameters)

    fun getBatteryStatus(): RfidBatteryStatus
}
