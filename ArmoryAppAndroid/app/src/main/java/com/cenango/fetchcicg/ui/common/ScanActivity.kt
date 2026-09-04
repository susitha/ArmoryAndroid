package com.cenango.fetchcicg.ui.common

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cenango.fetchcicg.BuildConfig
import com.cenango.fetchcicg.FetchCICGApplication
import com.cenango.fetchcicg.R
import com.cenango.fetchcicg.rfid.RfidError
import com.cenango.fetchcicg.rfid.RfidManagerListener
import com.cenango.fetchcicg.ui.checkin.EnterCheckinAssetDetailsActivity
import com.cenango.fetchcicg.ui.checkout.EnterCheckoutAssetDetailsActivity
import com.cenango.fetchcicg.ui.enroll.EnterAssetDetailsActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ScanFlow { ENROLL, CHECKIN, CHECKOUT }

/**
 * Kotlin/Android equivalent of iOS's `ScanViewController`
 * (Controllers/Common/ScanViewController.swift): shows RFID reader
 * connection/battery status and a single-tag scan, then hands the scanned
 * EPC tag off to the next screen for the active flow.
 *
 * iOS starts scanning when the reader's physical trigger key is pressed
 * (`AsReaderGUNManager.enableTriggerButton`). The Chainway SDK doesn't expose
 * that key as a callback — it arrives as a plain Android [KeyEvent], caught
 * below via [dispatchKeyEvent] against [TRIGGER_KEYCODES]. Every physical
 * unit tested so far has fired a *different* keycode for the same trigger
 * (293 on a C72, 294 on a C66 — both confirmed live via logcat during a real
 * press), so that's a set of confirmed values, not one "correct" code — see
 * its doc for how to add another. This screen also still auto-starts one
 * scan attempt on connect and lets the card be tapped to retry, both as
 * fallbacks in case a keycode isn't in the set yet.
 *
 * No reader connected (e.g. testing on an emulator, or before a physical C72
 * is available)? In debug builds, tapping the card simulates a tag read
 * instead of doing nothing, so the rest of the flow (Enter Details, pickers,
 * Summary, the real `POST /api/assets` call) can still be exercised. Mirrors
 * iOS's own `initiateReviewMode()`, which does the same thing (injects a
 * hardcoded EPC) for App Store review when no real reader is present.
 */
class ScanActivity : AppCompatActivity(), RfidManagerListener {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FLOW = "extra_flow"
        const val EXTRA_TAG = "extra_tag"

        // Physical trigger keycodes, each caught live via logcat during a
        // real press on real hardware — every Chainway model/unit tested so
        // far has fired a *different* one, so this is a set of confirmed
        // values rather than one "correct" code:
        //   293 — a C72 (KeyEvent keyCode=293, scanCode=186)
        //   294 — a C66 (WindowManager interceptKeyTi keyCode=294)
        // 280/139 are kept as unconfirmed fallbacks — those came from the
        // Chainway "keyboardemulator" system app's own UHF config on the C72
        // unit, which *didn't* match what that hardware actually fired once
        // its legacy scanning service was disabled; harmless to also check
        // them in case some other unit/firmware uses one of them for real.
        // If a new device model reports "trigger does nothing," the fix is
        // the same each time: `adb logcat` for `keyCode=` or `interceptKeyTi`
        // during a real press, then add the number here.
        private val TRIGGER_KEYCODES = setOf(293, 294, 280, 139)
    }

    private lateinit var app: FetchCICGApplication
    private lateinit var flow: ScanFlow

    private lateinit var stateTitle: TextView
    private lateinit var stateSubtitle: TextView
    private lateinit var stateIcon: ImageView
    private lateinit var tagText: TextView
    private lateinit var batteryText: TextView
    private lateinit var nextButton: MaterialButton

    private var scannedTag: String? = null

    // The Chainway SDK's RFIDWithUHFUART (unlike its Bluetooth/USB reader
    // classes) exposes no beep/buzzer API, so a successful scan gets its
    // audible feedback from here instead.
    private var toneGenerator: ToneGenerator? = null

    // Guards against tag reads that fire on their own, not from a scan we
    // actually asked for. On this hardware/SDK, stopRfidScanning() reliably
    // fails after a tag-mode read (confirmed via logcat: "UHF_StopGet: stop
    // failed" / "stopInventory() err :-1", every time) — the reader's
    // internal inventory loop never actually halts, so its callback keeps
    // firing a fresh tag read every ~10s indefinitely, with no trigger press
    // or tap involved. Set true right when a scan is requested, false once
    // its result (success or NO_TAG) has been handled; any onTagRead() that
    // arrives while false is one of those stray reads and is ignored.
    private var isScanInFlight = false

    // Tracks the pending beginScanningAfterDelay() coroutine so a redundant
    // reconnect (see beginScanningAfterDelay's doc) cancels the old one
    // instead of racing it with a second.
    private var scanLoopJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        app = application as FetchCICGApplication
        flow = ScanFlow.valueOf(intent.getStringExtra(EXTRA_FLOW) ?: ScanFlow.ENROLL.name)

        findViewById<TextView>(R.id.screenTitle).text =
            intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.enroll_title)

        stateTitle = findViewById(R.id.stateTitle)
        stateSubtitle = findViewById(R.id.stateSubtitle)
        stateIcon = findViewById(R.id.stateIcon)
        tagText = findViewById(R.id.tagText)
        batteryText = findViewById(R.id.batteryText)
        nextButton = findViewById(R.id.nextButton)

        findViewById<View>(R.id.cardContainer).setOnClickListener { attemptScan() }
        nextButton.setOnClickListener { onNext() }

        app.rfidManager.listener = this
        app.rfidManager.enableTriggerButton = true
        toneGenerator = try {
            // STREAM_MUSIC, not STREAM_NOTIFICATION: confirmed via logcat on a
            // C66 that STREAM_NOTIFICATION's AudioTrack was created and did
            // deliver frames, but produced no audible sound — this hardware
            // doesn't route that stream to a speaker path as reliably as
            // STREAM_MUSIC, which rugged/kiosk-style Android devices almost
            // always wire to the loudest built-in speaker.
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        } catch (e: RuntimeException) {
            null
        }

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
        if (app.rfidManager.listener === this) app.rfidManager.listener = null
        app.rfidManager.enableTriggerButton = false
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in TRIGGER_KEYCODES && app.rfidManager.enableTriggerButton) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                attemptScan()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun beginScanningAfterDelay() {
        // The reader's ConnectionStatusCallback can report CONNECTED twice in
        // quick succession right after this screen opens (confirmed via
        // logcat: onReaderConnected() firing once from onCreate's own
        // isDeviceConnected check, then again ~100ms later from a genuine
        // second callback). Without cancelling a still-pending scan loop,
        // that produced two independent delayed attemptScan() calls racing
        // each other — the app-visible symptom was scanning/beeping that
        // looked "automatic," worst on a screen's second visit.
        scanLoopJob?.cancel()
        scanLoopJob = lifecycleScope.launch {
            delay(1500)
            app.rfidManager.setTagRfidMode()
            app.rfidManager.clearMask()
            render(ScanUiState.SCANNING)
            attemptScan()
        }
    }

    private fun attemptScan() {
        isScanInFlight = true
        if (!app.rfidManager.isDeviceConnected) {
            if (BuildConfig.DEBUG) simulateTagRead()
            return
        }
        app.rfidManager.startRfidScanning()
    }

    /** Debug-only stand-in for a real scan when no reader is connected — see the class doc. */
    private fun simulateTagRead() {
        val randomSuffix = (1..18).map { "0123456789ABCDEF".random() }.joinToString("")
        Toast.makeText(this, "Simulated tag (debug build, no reader connected)", Toast.LENGTH_SHORT).show()
        onTagRead(tag = "3000E2$randomSuffix", rssi = 0f)
    }

    private fun updateBatteryLevel() {
        batteryText.text = app.rfidManager.getBatteryStatus().percentageText
    }

    private fun render(state: ScanUiState) {
        stateTitle.text = getString(state.textRes)
        stateTitle.setTextColor(ContextCompat.getColor(this, state.colorRes))
        stateSubtitle.text = state.infoRes?.let { getString(it) } ?: ""
        if (state == ScanUiState.DEVICE_NOT_CONNECTED && BuildConfig.DEBUG) {
            stateSubtitle.text = "${stateSubtitle.text}\n(Debug build: tap card to simulate a scan)"
        }
        stateIcon.setImageResource(state.iconRes)
        tagText.visibility = if (state == ScanUiState.DEVICE_NOT_CONNECTED) View.GONE else tagText.visibility
    }

    private fun onNext() {
        val tag = scannedTag ?: return
        when (flow) {
            ScanFlow.ENROLL -> {
                startActivity(
                    Intent(this, EnterAssetDetailsActivity::class.java)
                        .putExtra(EnterAssetDetailsActivity.EXTRA_TAG, tag)
                )
            }
            ScanFlow.CHECKOUT -> {
                startActivity(
                    Intent(this, EnterCheckoutAssetDetailsActivity::class.java)
                        .putExtra(EnterCheckoutAssetDetailsActivity.EXTRA_TAG, tag)
                )
            }
            ScanFlow.CHECKIN -> {
                startActivity(
                    Intent(this, EnterCheckinAssetDetailsActivity::class.java)
                        .putExtra(EnterCheckinAssetDetailsActivity.EXTRA_TAG, tag)
                )
            }
        }
    }

    // MARK: - RfidManagerListener

    override fun onReaderConnected() {
        render(ScanUiState.DEVICE_CONNECTED)
        updateBatteryLevel()
        beginScanningAfterDelay()
    }

    override fun onReaderDisconnected() {
        render(ScanUiState.DEVICE_NOT_CONNECTED)
        updateBatteryLevel()
    }

    override fun onErrorOccurred(error: RfidError) {
        isScanInFlight = false
        if (error == RfidError.NO_TAG) {
            Toast.makeText(this, "No tag found — tap to try again", Toast.LENGTH_SHORT).show()
            return
        }
        render(ScanUiState.FAILED)
        stateSubtitle.text = error.description
    }

    override fun onTagRead(tag: String, rssi: Float) {
        if (!isScanInFlight) return // Stray read from the reader's own inventory loop — see isScanInFlight's doc.
        isScanInFlight = false
        scannedTag = tag
        tagText.text = tag
        tagText.visibility = View.VISIBLE
        nextButton.isEnabled = true
        app.rfidManager.stopRfidScanning()
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    override fun onDeviceOverheated() {
        render(ScanUiState.OVERHEATED)
    }
}
