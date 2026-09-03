package com.cenango.fetchcicg.ui.inventory

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.rfid.RfidError
import com.cenango.fetchcicg.rfid.RfidManagerListener
import com.cenango.fetchcicg.ui.common.ScanUiState
import com.cenango.fetchcicg.ui.search.PulseView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `TakeInventoryViewController`
 * (Controllers/Inventory/TakeInventoryViewController.swift): continuous,
 * unmasked RFID inventory — collects every unique EPC seen — with a live
 * count and a pulsing "searching" indicator, started/stopped by one button.
 *
 * Unlike `LocateAssetActivity`, scanning does **not** auto-start on connect:
 * iOS only calls `showDeviceInfoViews(false)` (swap to the live-count/pulse
 * view) from the Start button's own tap handler; the connect-triggered delay
 * only updates the status text to "Scanning…" while the status card stays
 * visible. Mirrored here via [renderStatus] (always-available status display)
 * versus [startScanning]/[stopScanning] (the actual view swap + toggle,
 * gated on a manual tap).
 */
class TakeInventoryActivity : AppCompatActivity(), RfidManagerListener {

    private lateinit var app: FetchCICGApplication

    private lateinit var stateTitle: TextView
    private lateinit var stateSubtitle: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var scanningRow: View
    private lateinit var tagCountText: TextView
    private lateinit var pulseView: PulseView
    private lateinit var batteryText: TextView
    private lateinit var actionButton: MaterialButton

    private var isScanning = false
    private val foundTags = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_inventory)
        app = application as FetchCICGApplication

        stateTitle = findViewById(R.id.stateTitle)
        stateSubtitle = findViewById(R.id.stateSubtitle)
        statusIcon = findViewById(R.id.statusIcon)
        scanningRow = findViewById(R.id.scanningRow)
        tagCountText = findViewById(R.id.tagCountText)
        pulseView = findViewById(R.id.pulseView)
        batteryText = findViewById(R.id.batteryText)
        actionButton = findViewById(R.id.actionButton)

        val centerDot = findViewById<View>(R.id.centerDot)
        (centerDot.background.mutate() as GradientDrawable).setColor(ContextCompat.getColor(this, R.color.primary))

        actionButton.setOnClickListener { toggleScanning() }

        app.rfidManager.listener = this

        if (app.rfidManager.isDeviceConnected) {
            onConnected()
        } else {
            renderStatus(ScanUiState.DEVICE_NOT_CONNECTED)
        }
        updateBatteryLevel()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.rfidManager.stopRfidScanning()
        pulseView.stop()
        if (app.rfidManager.listener === this) app.rfidManager.listener = null
    }

    private fun onConnected() {
        renderStatus(ScanUiState.DEVICE_CONNECTED)
        actionButton.isEnabled = true
        lifecycleScope.launch {
            delay(1500)
            app.rfidManager.setSearchRfidMode()
            app.rfidManager.clearMask()
            if (!isScanning) renderStatus(ScanUiState.SCANNING)
        }
    }

    private fun toggleScanning() {
        if (isScanning) stopScanning() else startScanning()
    }

    private fun startScanning() {
        isScanning = true
        foundTags.clear()
        updateTagCount()
        actionButton.setText(R.string.locate_stop_button)

        statusIcon.visibility = View.GONE
        scanningRow.visibility = View.VISIBLE
        pulseView.start()

        app.rfidManager.startRfidScanning()
    }

    private fun stopScanning() {
        isScanning = false
        pulseView.stop()
        app.rfidManager.stopRfidScanning()

        startActivity(
            Intent(this, InventoryBreakdownActivity::class.java)
                .putExtra(InventoryBreakdownActivity.EXTRA_TAGS, foundTags.joinToString(","))
        )
    }

    private fun updateTagCount() {
        tagCountText.text = getString(R.string.inventory_tag_count_format, foundTags.size)
    }

    private fun updateBatteryLevel() {
        batteryText.text = app.rfidManager.getBatteryStatus().percentageText
    }

    private fun renderStatus(state: ScanUiState) {
        stateTitle.text = getString(state.textRes)
        stateTitle.setTextColor(ContextCompat.getColor(this, state.colorRes))
        stateSubtitle.text = state.infoRes?.let { getString(it) } ?: ""
        statusIcon.setImageResource(state.iconRes)

        if (!isScanning) {
            statusIcon.visibility = View.VISIBLE
            scanningRow.visibility = View.GONE
        }
    }

    // MARK: - RfidManagerListener

    override fun onReaderConnected() {
        onConnected()
        updateBatteryLevel()
    }

    override fun onReaderDisconnected() {
        isScanning = false
        pulseView.stop()
        actionButton.isEnabled = false
        renderStatus(ScanUiState.DEVICE_NOT_CONNECTED)
        updateBatteryLevel()
    }

    /**
     * iOS updates the status text here without forcing the view back from
     * the live-count/pulse display, which can leave an error invisible while
     * still "scanning" — a deliberate small deviation: force back to the
     * status card so the problem is actually seen, instead of copying that.
     */
    override fun onErrorOccurred(error: RfidError) {
        if (error == RfidError.NO_TAG) return
        isScanning = false
        pulseView.stop()
        renderStatus(ScanUiState.FAILED)
        stateSubtitle.text = error.description
        actionButton.isEnabled = false
    }

    override fun onTagRead(tag: String, rssi: Float) {
        if (foundTags.add(tag)) updateTagCount()
    }

    override fun onDeviceOverheated() {
        isScanning = false
        pulseView.stop()
        renderStatus(ScanUiState.OVERHEATED)
    }
}
