package com.cenango.fetchcicg.ui.common

import android.content.Intent
import android.os.Bundle
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
 * that key as a callback yet (see the TODO in `ChainwayRfidManager`), so this
 * screen auto-starts one scan attempt on connect and also lets the card be
 * tapped to retry — a temporary stand-in until the trigger key is wired up.
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
    }

    private fun beginScanningAfterDelay() {
        lifecycleScope.launch {
            delay(1500)
            app.rfidManager.setTagRfidMode()
            app.rfidManager.clearMask()
            render(ScanUiState.SCANNING)
            attemptScan()
        }
    }

    private fun attemptScan() {
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
        if (error == RfidError.NO_TAG) {
            Toast.makeText(this, "No tag found — tap to try again", Toast.LENGTH_SHORT).show()
            return
        }
        render(ScanUiState.FAILED)
        stateSubtitle.text = error.description
    }

    override fun onTagRead(tag: String, rssi: Float) {
        scannedTag = tag
        tagText.text = tag
        tagText.visibility = View.VISIBLE
        nextButton.isEnabled = true
        app.rfidManager.stopRfidScanning()
    }

    override fun onDeviceOverheated() {
        render(ScanUiState.OVERHEATED)
    }
}
