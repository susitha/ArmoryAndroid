package com.cenango.fetchcicg.ui.search

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Hand-rolled stand-in for iOS's `Pulsator` library (`LocateAssetViewController.swift`):
 * a small set of rings that expand and fade out from the center, looping,
 * to indicate "actively searching". Deliberately does not draw the iOS
 * `device` image behind it — that PNG is a literal photo of a phone clipped
 * into the old AsReader accessory sled, the same wrong-hardware problem as
 * elsewhere in this port (see the `armory-android-hardware-swap` memory) —
 * a plain colored dot stands in instead, drawn by [LocateAssetActivity].
 */
class PulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val RING_COUNT = 3
        const val CYCLE_MS = 3000L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#496BB3")
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var phase = 0f

    fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        phase = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (animator == null) return

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = minOf(width, height) / 2f

        for (i in 0 until RING_COUNT) {
            val ringPhase = (phase + i.toFloat() / RING_COUNT) % 1f
            val radius = maxRadius * ringPhase
            paint.alpha = ((1f - ringPhase) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
    }
}
