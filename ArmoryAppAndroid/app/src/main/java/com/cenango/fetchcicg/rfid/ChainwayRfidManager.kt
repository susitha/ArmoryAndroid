package com.cenango.fetchcicg.rfid

import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rscja.deviceapi.exception.ConfigurationException
import com.rscja.deviceapi.interfaces.ConnectionStatus
import com.rscja.deviceapi.interfaces.ConnectionStatusCallback
import com.rscja.deviceapi.interfaces.IUHF
import java.util.concurrent.Executors

/**
 * [RfidManager] backed by the Chainway C72's built-in UHF module, via the
 * `com.rscja.deviceapi` SDK (API_Ver20251103, from
 * https://www.chainway.net/Support/Info/10 — the "API_Ver..." download for
 * Android Studio). Verified against that SDK's bundled Javadoc
 * (`API_Ver20251103/doc/com/rscja/deviceapi/`); trigger-key wiring and the
 * power-gain range have both since been confirmed against physical hardware
 * — see the trigger-key note in `init` below and [POWER_GAIN_MAX]'s doc.
 *
 * The SDK's own class is `RFIDWithUHFUART`: a singleton
 * (`RFIDWithUHFUART.getInstance()`) covering serial/UART-connected UHF
 * modules, of which the C72's internal module is one. Unlike the old AsReader
 * gun, there's no separate accessory battery — the module shares the
 * handheld's own battery, so [getBatteryStatus] reads that via Android's
 * [BatteryManager] instead of anything UHF-specific.
 */
class ChainwayRfidManager(private val context: Context) : RfidManager {

    companion object {
        private const val TAG = "ChainwayRfidManager"

        // The SDK's Javadoc for setPower()/getPower() doesn't state the valid
        // range, and no bundled demo/docs cross-reference it either — but
        // getPower() called right after a real connect (on a C66) read back
        // 30, matching this MAX exactly, confirming 30 is a genuine in-range
        // firmware value rather than a guess. MIN (5) is unconfirmed the same
        // way — a live low-end reading would need setPower() to actually
        // succeed on hardware, which it hasn't during this work (see
        // stopInventory()/setPower()/setFilter()'s reliability issue in
        // SCAFFOLD.md) — treat 5 as a reasonable conservative floor, not a
        // verified one.
        private const val POWER_GAIN_MIN = 5
        private const val POWER_GAIN_MAX = 30
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Every call into the SDK's RFIDWithUHFUART goes through this single
    // thread, in submission order — not just inventorySingleTag(). On this
    // hardware/SDK, setPower()/setFilter()/stopInventory() are confirmed
    // (via logcat) to block for ~2-2.5s while internally retrying before
    // failing, every time; running them on the caller's thread (they used to
    // run directly on whatever thread called them, often the main thread via
    // ScanActivity's coroutine) turned that into felt UI delay/jank on every
    // scan. A single-thread executor keeps command ordering (e.g. setPower
    // before the read that depends on it) without blocking any UI thread.
    private val uartExecutor = Executors.newSingleThreadExecutor()

    private var uhf: RFIDWithUHFUART? = null
    private var continuousMode = false

    override var listener: RfidManagerListener? = null

    // The C72's 2D barcode engine is a separate module from the UHF one (see
    // the SDK's "Demo-2D" sample) and isn't exposed through this class.
    override val hasBarcodeSupport: Boolean? = null
    override val hasRfidSupport: Boolean get() = uhf != null

    override var isDeviceConnected: Boolean = false
        private set

    override var enableTriggerButton: Boolean = false

    init {
        try {
            val reader = RFIDWithUHFUART.getInstance()
            uhf = reader

            val connected = reader.init(context)
            isDeviceConnected = connected

            reader.setConnectionStatusCallback(ConnectionStatusCallback<Any?> { status, _ ->
                Log.d(TAG, "ConnectionStatusCallback: $status")
                mainHandler.post {
                    when (status) {
                        ConnectionStatus.CONNECTED -> {
                            isDeviceConnected = true
                            listener?.onReaderConnected()
                        }
                        ConnectionStatus.DISCONNECTED -> {
                            isDeviceConnected = false
                            listener?.onReaderDisconnected()
                        }
                        else -> Unit // CONNECTING — nothing to surface.
                    }
                }
            })

            // Must be set before startInventoryTag() per the SDK's own docs.
            reader.setInventoryCallback { tagInfo: UHFTAGInfo ->
                Log.d(TAG, "setInventoryCallback fired: EPC=${tagInfo.getEPC()}")
                mainHandler.post {
                    val rssi = tagInfo.getRssi()?.toFloatOrNull() ?: 0f
                    listener?.onTagRead(tagInfo.getEPC(), rssi)
                }
            }

            if (connected) {
                mainHandler.post { listener?.onReaderConnected() }
            }

            // Physical scan trigger key: the SDK doesn't expose it as a
            // callback, so it's caught as a plain Android KeyEvent at the
            // Activity level instead — see ScanActivity.dispatchKeyEvent()
            // and its TRIGGER_KEYCODES set, gated on [enableTriggerButton].
            // Different physical units report different keycodes for the
            // same trigger (293 on a C72, 294 on a C66, both confirmed live
            // via logcat) — see ScanActivity's class doc. Only ScanActivity
            // wires this up — LocateAssetActivity/TakeInventoryActivity use
            // continuous scanning and don't need it.
        } catch (e: ConfigurationException) {
            // Thrown by getInstance() if this device/firmware has no UHF-over-UART module.
            Log.e(TAG, "No UHF module available", e)
        }
    }

    override fun setTagRfidMode() {
        // Mirrors AsReaderGUNManager.setTagRFIDMode(): single read, minimum power.
        continuousMode = false
        uartExecutor.execute { uhf?.setPower(POWER_GAIN_MIN) }
    }

    override fun setSearchRfidMode() {
        // Mirrors AsReaderGUNManager.setSearchRFIDMode(): continuous read, maximum power.
        // Note: unlike the AsReader gun, UHFTAGInfo has no distance/RSSI "mode"
        // toggle to enable separately — getRssi() is always populated on read.
        continuousMode = true
        uartExecutor.execute { uhf?.setPower(POWER_GAIN_MAX) }
    }

    override fun startRfidScanning() {
        val reader = uhf ?: return
        Log.d(TAG, "startRfidScanning() continuousMode=$continuousMode")

        if (continuousMode) {
            // Must go through uartExecutor, not run directly on the caller's
            // thread — setSearchRfidMode()/clearMask()/setMask() all queue
            // their setPower()/setFilter() calls there asynchronously, and
            // LocateAssetActivity calls this right after them with no wait.
            // Without this, startInventoryTag() could fire on the UART before
            // a just-queued setFilter() actually finished — a real,
            // reproduced race: it only ever hit on a screen's *first* locate
            // attempt (the one sequence that chains setSearchRfidMode ->
            // clearMask -> setMask -> startRfidScanning back to back), never
            // on a manual Stop/Start after that (which calls this alone, no
            // preceding setFilter queued). See SCAFFOLD.md.
            uartExecutor.execute { reader.startInventoryTag() }
        } else {
            // inventorySingleTag() blocks on UART I/O, so run it off the main thread.
            uartExecutor.execute {
                val tag = reader.inventorySingleTag()
                Log.d(TAG, "inventorySingleTag() returned: ${tag?.getEPC()}")
                mainHandler.post {
                    if (tag != null) {
                        listener?.onTagRead(tag.getEPC(), tag.getRssi()?.toFloatOrNull() ?: 0f)
                    } else {
                        listener?.onErrorOccurred(RfidError.NO_TAG)
                    }
                }
            }
        }
    }

    override fun stopRfidScanning() {
        uartExecutor.execute {
            val result = uhf?.stopInventory()
            Log.d(TAG, "stopRfidScanning() -> stopInventory() returned $result")
        }
    }

    override fun clearMask() {
        uartExecutor.execute { uhf?.setFilter(IUHF.Bank_EPC, 0, 0, "") }
    }

    override fun setMask(epc: String, parameters: EpcMaskParameters) {
        val start = parameters.startIndex
        val end = start + parameters.maskLength
        require(start >= 0 && end <= epc.length) {
            "Cannot create a mask with the given data"
        }

        val maskString = epc.substring(start, end)
        Log.d(TAG, "setMask() -> $maskString")

        // setFilter(bank, ptr, cnt, data): ptr/cnt are in bits, same units iOS's
        // `offset`/`tagLength` already use for AsSelectMaskEPCParam.
        uartExecutor.execute { uhf?.setFilter(IUHF.Bank_EPC, parameters.offset, parameters.tagLength, maskString) }
    }

    override fun getBatteryStatus(): RfidBatteryStatus {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val percent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return RfidBatteryStatus.fromPercentage(percent?.takeIf { it in 0..100 })
    }

    // NOTE: RfidManagerListener.onDeviceOverheated() is never called here. The
    // SDK doesn't push an overheat callback the way AsReader's module did —
    // RFIDWithUHFUART only exposes a polled getTemperature() (°C, no
    // documented safe/danger threshold). If overheat protection is needed,
    // poll it on a timer during scanning and pick a threshold empirically on
    // a real C72 rather than guessing one here.
}
