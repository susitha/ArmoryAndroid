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
 * (`API_Ver20251103/doc/com/rscja/deviceapi/`), not against a physical C72 —
 * confirm on real hardware before shipping, especially the power-gain range
 * and the trigger-key wiring (both marked TODO below).
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

        // TODO: the SDK's Javadoc for setPower()/getPower() doesn't state the
        // valid range — confirm against the C72 data sheet or by calling
        // getPower() after init() to see what firmware default comes back.
        private const val POWER_GAIN_MIN = 5
        private const val POWER_GAIN_MAX = 30
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val singleReadExecutor = Executors.newSingleThreadExecutor()

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
                mainHandler.post {
                    val rssi = tagInfo.getRssi()?.toFloatOrNull() ?: 0f
                    listener?.onTagRead(tagInfo.getEPC(), rssi)
                }
            }

            if (connected) {
                mainHandler.post { listener?.onReaderConnected() }
            }

            // TODO: wire up the physical scan trigger key. Chainway handhelds
            // deliver it as an Android KeyEvent (device/firmware-specific
            // keycode, commonly exposed via Activity.dispatchKeyEvent /
            // onKeyDown rather than through this SDK) — confirm the keycode
            // against the C72 manual, then call startRfidScanning() /
            // stopRfidScanning() on down/up while [enableTriggerButton] is
            // true, mirroring AsReaderGUNManager's `on(asReaderTriggerKeyEvent:)`.
        } catch (e: ConfigurationException) {
            // Thrown by getInstance() if this device/firmware has no UHF-over-UART module.
            Log.e(TAG, "No UHF module available", e)
        }
    }

    override fun setTagRfidMode() {
        // Mirrors AsReaderGUNManager.setTagRFIDMode(): single read, minimum power.
        continuousMode = false
        uhf?.setPower(POWER_GAIN_MIN)
    }

    override fun setSearchRfidMode() {
        // Mirrors AsReaderGUNManager.setSearchRFIDMode(): continuous read, maximum power.
        // Note: unlike the AsReader gun, UHFTAGInfo has no distance/RSSI "mode"
        // toggle to enable separately — getRssi() is always populated on read.
        continuousMode = true
        uhf?.setPower(POWER_GAIN_MAX)
    }

    override fun startRfidScanning() {
        val reader = uhf ?: return

        if (continuousMode) {
            reader.startInventoryTag()
        } else {
            // inventorySingleTag() blocks on UART I/O, so run it off the main thread.
            singleReadExecutor.execute {
                val tag = reader.inventorySingleTag()
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
        uhf?.stopInventory()
    }

    override fun clearMask() {
        uhf?.setFilter(IUHF.Bank_EPC, 0, 0, "")
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
        uhf?.setFilter(IUHF.Bank_EPC, parameters.offset, parameters.tagLength, maskString)
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
