package com.cenango.fetchcicg.ui.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Hand-drawn stand-in for iOS's `FDBarGauge` (a third-party library) in
 * `LocateAssetViewController.swift`: a stack of discrete segments (not one
 * continuous fill) whose lit color reflects an RSSI reading — red at the
 * bottom, yellow in the middle, green/dark-green at the top, matching a
 * reference screenshot of the real iOS screen. iOS maps -95..-45 dBm across
 * red→yellow→green with hard thresholds at 35%/80%; this replicates that
 * (an earlier version interpolated continuously instead — a deliberate
 * simplification at the time, revisited once the segmented look was
 * specifically requested against the reference).
 */
class SignalGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val MIN_RSSI = -95f
        private const val MAX_RSSI = -45f
        private const val SEGMENT_COUNT = 10
        private const val RED_YELLOW_THRESHOLD = 0.35f
        private const val YELLOW_GREEN_THRESHOLD = 0.80f
        private const val GAP_FRACTION_OF_HEIGHT = 0.015f

        /** 0f (weakest/no signal) to 1f (strongest) — also used by LocateAssetActivity's proximity beep. */
        fun ratioFor(rssi: Float): Float = (rssi.coerceIn(MIN_RSSI, MAX_RSSI) - MIN_RSSI) / (MAX_RSSI - MIN_RSSI)
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0E0E0") }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF3B30") }
    private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFCC00") }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#34C759") }
    private val darkGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B7A2E") }
    private val segmentRect = RectF()
    private val segmentPath = Path()

    /** 0f (no signal) to 1f (strongest); null shows every segment unlit. */
    private var ratio: Float? = null

    fun setRssi(rssi: Float) {
        ratio = ratioFor(rssi)
        invalidate()
    }

    fun clear() {
        ratio = null
        invalidate()
    }

    /** The color a segment lights up as, based on the RSSI range it represents — not the live reading. */
    private fun paintForSegment(segmentTopRatio: Float): Paint = when {
        segmentTopRatio <= RED_YELLOW_THRESHOLD -> redPaint
        segmentTopRatio <= YELLOW_GREEN_THRESHOLD -> yellowPaint
        segmentTopRatio >= 1f -> darkGreenPaint // topmost segment only, for an "excellent" cap
        else -> greenPaint
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = height * GAP_FRACTION_OF_HEIGHT
        val segmentHeight = (height - gap * (SEGMENT_COUNT - 1)) / SEGMENT_COUNT
        val cornerRadius = width * 0.3f
        val currentRatio = ratio

        for (i in 0 until SEGMENT_COUNT) {
            // i=0 is the bottom-most segment (weakest); SEGMENT_COUNT-1 is the top (strongest).
            val bottom = height - i * (segmentHeight + gap)
            val top = bottom - segmentHeight
            segmentRect.set(0f, top, width.toFloat(), bottom)

            val segmentBottomRatio = i.toFloat() / SEGMENT_COUNT
            val segmentTopRatio = (i + 1f) / SEGMENT_COUNT
            val isLit = currentRatio != null && currentRatio >= segmentBottomRatio
            val paint = if (isLit) paintForSegment(segmentTopRatio) else trackPaint

            // Only round the true outer corners of the whole stack — the top
            // corners of the top segment, bottom corners of the bottom one —
            // so every segment fills its full allotted height/width instead
            // of every segment looking inset on all four corners (the first
            // version rounded all four corners of every segment, which read
            // as the first/last segments being visibly smaller than the rest).
            val topRadius = if (i == SEGMENT_COUNT - 1) cornerRadius else 0f
            val bottomRadius = if (i == 0) cornerRadius else 0f
            segmentPath.reset()
            segmentPath.addRoundRect(
                segmentRect,
                floatArrayOf(
                    topRadius, topRadius, topRadius, topRadius,
                    bottomRadius, bottomRadius, bottomRadius, bottomRadius
                ),
                Path.Direction.CW
            )
            canvas.drawPath(segmentPath, paint)
        }
    }
}
