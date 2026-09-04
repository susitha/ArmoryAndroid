package com.cenango.fetchcicg.ui.search

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
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
import com.cenango.fetchcicg.ui.common.bindAssetHeader
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
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
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_ASSET_CATEGORY_NAME = "extra_asset_category_name"
        const val EXTRA_ASSET_SERIAL_NUMBER = "extra_asset_serial_number"

        // Proximity beep: interval between beeps shrinks as the signal gets
        // stronger, like a metal detector/geiger counter. MIN is close to
        // the reader's own read cadence (~80ms between callbacks), so it
        // reads as near-continuous right on top of the tag; MAX is a lazy
        // beep at the edge of range.
        private const val BEEP_INTERVAL_MAX_MS = 700L
        private const val BEEP_INTERVAL_MIN_MS = 90L
        private const val BEEP_DURATION_MS = 60

        // Watchdog: on this SDK, continuous inventory can go completely
        // silent mid-session — confirmed via logcat (a setFilter() err :-1,
        // then zero further setInventoryCallback firings, ever) — reported
        // by the user as the gauge freezing on a strong (green) reading,
        // recoverable only by manually tapping Stop then Start again. Same
        // native reliability issue as stopInventory()/setFilter() elsewhere
        // (see SCAFFOLD.md), just a different failure mode: not "the command
        // failed" but "the whole read loop stopped."
        //
        // Can't tell this apart from "genuinely no tag in range right now"
        // from timing alone — continuous mode has no NO_TAG callback the way
        // single-tag Tag mode does (onErrorOccurred(NO_TAG) is never called
        // from continuousMode's startInventoryTag() path), so total silence
        // is the only signal either way. The timeout is deliberately long
        // (well past a normal "walking the room, nothing nearby yet" gap) to
        // keep false restarts rare; a restart itself is cheap and harmless
        // when nothing's actually wrong, since it just re-arms the same
        // continuous scan that was already listening.
        private const val WATCHDOG_CHECK_INTERVAL_MS = 2000L
        private const val WATCHDOG_SILENCE_TIMEOUT_MS = 6000L
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

    // Tracks the pending beginScanningAfterDelay() coroutine so a redundant
    // reconnect (the reader's ConnectionStatusCallback can report CONNECTED
    // twice within ~100ms of this screen opening — confirmed via logcat, the
    // same root cause fixed in ScanActivity's scanLoopJob) cancels the old
    // one instead of racing it with a second, concurrent setup sequence.
    private var scanLoopJob: Job? = null

    private var toneGenerator: ToneGenerator? = null
    private var lastBeepAtMs = 0L

    private var watchdogJob: Job? = null
    private var lastReadAtMs = 0L

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

        bindAssetHeader(
            findViewById(R.id.assetHeader),
            intent.getStringExtra(EXTRA_ASSET_NAME).orEmpty(),
            intent.getStringExtra(EXTRA_ASSET_CATEGORY_NAME).orEmpty(),
            intent.getStringExtra(EXTRA_ASSET_SERIAL_NUMBER).orEmpty()
        )

        actionButton.setOnClickListener { toggleLocating() }

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        } catch (e: RuntimeException) {
            null
        }

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
        toneGenerator?.release()
        toneGenerator = null
        watchdogJob?.cancel()
    }

    private fun beginScanningAfterDelay() {
        scanLoopJob?.cancel()
        scanLoopJob = lifecycleScope.launch {
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
        lastReadAtMs = SystemClock.elapsedRealtime()
        startWatchdog()
    }

    private fun stopLocating() {
        isLocating = false
        actionButton.setText(R.string.locate_start_button)
        pulseView.stop()
        app.rfidManager.stopRfidScanning()
        watchdogJob?.cancel()
    }

    /** See WATCHDOG_* constants' doc for why this exists. */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            while (true) {
                delay(WATCHDOG_CHECK_INTERVAL_MS)
                val silentFor = SystemClock.elapsedRealtime() - lastReadAtMs
                if (silentFor > WATCHDOG_SILENCE_TIMEOUT_MS) {
                    app.rfidManager.stopRfidScanning()
                    app.rfidManager.startRfidScanning()
                    lastReadAtMs = SystemClock.elapsedRealtime()
                }
            }
        }
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
        // Any read at all — even for the wrong tag — proves the continuous
        // read loop is still alive, so the watchdog shouldn't restart it.
        lastReadAtMs = SystemClock.elapsedRealtime()

        // The hardware mask (setMask()) is meant to filter continuous
        // inventory down to just this asset's EPC, but on this SDK the
        // underlying setFilter() call is unreliable (same reliability issue
        // as stopInventory() — see SCAFFOLD.md), so a read for a *different*
        // tag can still arrive here. Confirmed via logcat: two different
        // EPCs interleaved in the same session, feeding unrelated RSSI
        // values into the gauge and making it look stuck/wrong. Filter
        // client-side as a safety net regardless of whether the mask held.
        if (tag != this.tag) return
        signalGauge.setRssi(rssi)
        beepForProximity(rssi)
    }

    /** Proximity beep: fires more often as the signal (and so the ratio) gets stronger. */
    private fun beepForProximity(rssi: Float) {
        val ratio = SignalGaugeView.ratioFor(rssi)
        val requiredInterval = BEEP_INTERVAL_MAX_MS - (BEEP_INTERVAL_MAX_MS - BEEP_INTERVAL_MIN_MS) * ratio
        val now = SystemClock.elapsedRealtime()
        if (now - lastBeepAtMs < requiredInterval) return
        lastBeepAtMs = now
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
    }

    override fun onDeviceOverheated() {
        render(ScanUiState.OVERHEATED)
    }
}
