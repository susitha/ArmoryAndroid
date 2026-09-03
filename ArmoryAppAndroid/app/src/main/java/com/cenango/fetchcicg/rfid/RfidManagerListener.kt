package com.cenango.fetchcicg.rfid

/**
 * Kotlin equivalent of iOS's `AsReaderGUNManagerDelegate` (AsReaderGUNManager+Delegate.swift).
 * All callbacks fire on the main thread.
 */
interface RfidManagerListener {
    fun onReaderConnected()
    fun onReaderDisconnected()
    fun onErrorOccurred(error: RfidError)
    fun onTagRead(tag: String, rssi: Float)
    fun onDeviceOverheated()
}
