package com.cenango.fetchcicg.ui.search

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
import com.cenango.fetchcicg.rfid.EpcMaskParameters
import com.cenango.fetchcicg.rfid.RfidError
import com.cenango.fetchcicg.rfid.RfidManagerListener
import com.cenango.fetchcicg.ui.common.ScanUiState
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kotlin/Android equivalent of iOS's `LocateAssetViewController`
 * (Controllers/Search/LocateAssetViewController.swift): masks continuous
 * RFID inventory to one specific EPC (the scanned/selected asset's tag) and
 * shows its RSSI as a signal-strength gauge, with a pulsing "searching"
 * indicator. Unlike `ScanActivity`'s single-tag "Tag mode", this uses
 * genuine continuous scanning (`RfidManager.startRfidScanning()` while in
 * search mode calls the Chainway SDK's real continuous-inventory callback,
 * `RFIDWithUHFUART.startInventoryTag()`) — it does **not** depend on the
 * physical trigger key the way single-shot Tag mode does, so there's no
 * debug-simulation stand-in needed here the way `ScanActivity` has one.
 */
class LocateAssetActivity : AppCompatActivity(), RfidManagerListener {

    companion object {
        const val EXTRA_TAG = "extra_tag"
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var tag: String

    private lateinit var stateTitle: TextView
    private lateinit var stateSubtitle: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var gaugeRow: View
    private lateinit var signalGauge: SignalGaugeView
    private lateinit var pulseView: PulseView
    private lateinit var batteryText: TextView
    private lateinit var actionButton: MaterialButton

    private var isLocating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locate_asset)
        app = application as FetchCICGApplication
        tag = intent.getStringExtra(EXTRA_TAG).orEmpty()

        stateTitle = findViewById(R.id.stateTitle)
        stateSubtitle = findViewById(R.id.stateSubtitle)
        statusIcon = findViewById(R.id.statusIcon)
        gaugeRow = findViewById(R.id.gaugeRow)
        signalGauge = findViewById(R.id.signalGauge)
        pulseView = findViewById(R.id.pulseView)
        batteryText = findViewById(R.id.batteryText)
        actionButton = findViewById(R.id.actionButton)

        // The center dot inside the pulse rings — same primary blue as the rings themselves.
        val centerDot = findViewById<View>(R.id.centerDot)
        (centerDot.background.mutate() as GradientDrawable).setColor(ContextCompat.getColor(this, R.color.primary))

        actionButton.setOnClickListener { toggleLocating() }

        app.rfidManager.listener = this

        if (app.rfidManager.isDeviceConnected) {
            render(ScanUiState.DEVICE_CONNECTED)
            beginScanningAfterDelay()
        } else {
            render(ScanUiState.DEVICE_NOT_CONNECTED)
        }
        updateBatteryLevel()
    }

    override fun onDestroy() {
        super.onDestroy()
        app.rfidManager.stopRfidScanning()
        pulseView.stop()
        if (app.rfidManager.listener === this) app.rfidManager.listener = null
    }

    private fun beginScanningAfterDelay() {
        lifecycleScope.launch {
            delay(1000)
            app.rfidManager.setSearchRfidMode()
            app.rfidManager.clearMask()
            setTagToSearch()
        }
    }

    /** Mirrors `setTagToSearch()`: mask continuous inventory to just this EPC, then start. */
    private fun setTagToSearch() {
        val format = app.storage.tagFormat
        if (format == null) {
            stateTitle.text = getString(R.string.locate_no_mask_data)
            return
        }
        try {
            app.rfidManager.setMask(
                tag,
                EpcMaskParameters(startIndex = format.maskIndex, tagLength = format.length, offset = format.offset, maskLength = format.maskLength)
            )
            startLocating()
        } catch (e: IllegalArgumentException) {
            stateTitle.text = e.message
        }
    }

    private fun toggleLocating() {
        if (isLocating) stopLocating() else startLocating()
    }

    private fun startLocating() {
        isLocating = true
        actionButton.isEnabled = true
        actionButton.setText(R.string.locate_stop_button)
        render(ScanUiState.DEVICE_CONNECTED)
        app.rfidManager.startRfidScanning()
    }

    private fun stopLocating() {
        isLocating = false
        actionButton.setText(R.string.locate_start_button)
        pulseView.stop()
        app.rfidManager.stopRfidScanning()
    }

    private fun updateBatteryLevel() {
        batteryText.text = app.rfidManager.getBatteryStatus().percentageText
    }

    private fun render(state: ScanUiState) {
        stateTitle.text = getString(state.textRes)
        stateTitle.setTextColor(ContextCompat.getColor(this, state.colorRes))
        stateSubtitle.text = state.infoRes?.let { getString(it) } ?: ""

        val activelyLocating = state == ScanUiState.DEVICE_CONNECTED
        statusIcon.visibility = if (activelyLocating) View.GONE else View.VISIBLE
        gaugeRow.visibility = if (activelyLocating) View.VISIBLE else View.GONE
        if (!activelyLocating) {
            statusIcon.setImageResource(state.iconRes)
            actionButton.isEnabled = false
            pulseView.stop()
        } else {
            pulseView.start()
        }
    }

    // MARK: - RfidManagerListener

    override fun onReaderConnected() {
        render(ScanUiState.DEVICE_CONNECTED)
        updateBatteryLevel()
        beginScanningAfterDelay()
    }

    override fun onReaderDisconnected() {
        isLocating = false
        render(ScanUiState.DEVICE_NOT_CONNECTED)
        updateBatteryLevel()
    }

    override fun onErrorOccurred(error: RfidError) {
        if (error == RfidError.NO_TAG) return // expected while nothing matching the mask is in range
        render(ScanUiState.FAILED)
        stateSubtitle.text = error.description
    }

    override fun onTagRead(tag: String, rssi: Float) {
        signalGauge.setRssi(rssi)
    }

    override fun onDeviceOverheated() {
        render(ScanUiState.OVERHEATED)
    }
}
