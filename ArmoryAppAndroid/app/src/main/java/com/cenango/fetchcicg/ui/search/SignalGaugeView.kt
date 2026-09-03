package com.cenango.fetchcicg.ui.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * Simple hand-drawn stand-in for iOS's `FDBarGauge` (a third-party library) in
 * `LocateAssetViewController.swift`: a vertical bar whose fill height and
 * color reflect an RSSI reading. iOS maps -95..-45 dBm across red→yellow→
 * green with hard thresholds at 35%/80%; this interpolates the same three
 * colors continuously across the same range instead of replicating the exact
 * threshold logic — a deliberate simplification, not an attempt at a pixel
 * clone of a UI library this project doesn't depend on.
 */
class SignalGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val MIN_RSSI = -95f
        private const val MAX_RSSI = -45f
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0E0E0") }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val fillRect = RectF()

    /** 0f (no signal) to 1f (strongest); null shows an empty track. */
    private var ratio: Float? = null

    fun setRssi(rssi: Float) {
        val clamped = rssi.coerceIn(MIN_RSSI, MAX_RSSI)
        ratio = (clamped - MIN_RSSI) / (MAX_RSSI - MIN_RSSI)
        fillPaint.color = colorFor(ratio!!)
        invalidate()
    }

    fun clear() {
        ratio = null
        invalidate()
    }

    private fun colorFor(ratio: Float): Int {
        // red -> yellow (0..0.5), yellow -> green (0.5..1)
        val (from, to, localRatio) = if (ratio < 0.5f) {
            Triple(Color.parseColor("#FF3B30"), Color.parseColor("#FFCC00"), ratio / 0.5f)
        } else {
            Triple(Color.parseColor("#FFCC00"), Color.parseColor("#34C759"), (ratio - 0.5f) / 0.5f)
        }
        return Color.rgb(
            lerp(Color.red(from), Color.red(to), localRatio),
            lerp(Color.green(from), Color.green(to), localRatio),
            lerp(Color.blue(from), Color.blue(to), localRatio)
        )
    }

    private fun lerp(a: Int, b: Int, ratio: Float): Int = (a + (b - a) * ratio).roundToInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = width / 2f
        trackRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val currentRatio = ratio ?: return
        val fillHeight = height * currentRatio
        fillRect.set(0f, height - fillHeight, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
    }
}
