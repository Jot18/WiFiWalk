package dev.wifiwalk.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Floor plan and estimated coverage drawn entirely on-device. Tap produces normalized coordinates. */
class SurveyMapView(context: Context) : View(context) {
    var onMapTap: ((Float, Float) -> Unit)? = null
    var samples: List<SignalSample> = emptyList()
        set(value) { field = value; invalidate() }
    var heatmapVisible = true
        set(value) { field = value; invalidate() }
    var plan: Bitmap? = null
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density
    private fun dp(n: Float) = n * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(10f)
        val available = RectF(pad, pad, width - pad, height - pad)
        val bmp = plan
        if (bmp != null) {
            val scale = min(available.width() / bmp.width, available.height() / bmp.height)
            val imageW = bmp.width * scale
            val imageH = bmp.height * scale
            val left = available.centerX() - imageW / 2
            val top = available.centerY() - imageH / 2
            rect.set(left, top, left + imageW, top + imageH)
        } else {
            rect.set(available)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(19, 33, 51)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)
        canvas.save()
        canvas.clipRect(rect)
        if (bmp != null) {
            paint.alpha = 255
            canvas.drawBitmap(bmp, null, rect, paint)
            // Dim bright plans slightly to preserve readable sensor markers.
            paint.color = Color.argb(40, 4, 17, 31)
            canvas.drawRect(rect, paint)
        } else {
            paint.color = Color.argb(45, 153, 173, 196)
            paint.strokeWidth = dp(1f)
            for (x in 0..10) {
                val xx = rect.left + rect.width() * x / 10f
                canvas.drawLine(xx, rect.top, xx, rect.bottom, paint)
            }
            for (y in 0..10) {
                val yy = rect.top + rect.height() * y / 10f
                canvas.drawLine(rect.left, yy, rect.right, yy, paint)
            }
        }
        if (heatmapVisible && samples.size >= 2) renderHeatmap(canvas)
        samples.forEachIndexed { index, s ->
            val x = rect.left + rect.width() * s.x
            val y = rect.top + rect.height() * s.y
            val color = strengthColor(s.rssi)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(75, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(x, y, dp(17f), paint)
            paint.color = color
            canvas.drawCircle(x, y, dp(10f), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            paint.textSize = dp(9f)
            canvas.drawText("${index + 1}", x, y + dp(3f), paint)
        }
        if (samples.isEmpty()) {
            paint.color = Color.rgb(160, 178, 196)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(13f)
            paint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText("Tap anywhere to take your first reading", rect.centerX(), rect.centerY(), paint)
        }
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = Color.rgb(55, 77, 102)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), paint)
        paint.style = Paint.Style.FILL
    }

    /** Inverse-distance weighted interpolation, intentionally clipped near measured locations. */
    private fun renderHeatmap(canvas: Canvas) {
        val cell = dp(11f)
        val nx = max(1, (rect.width() / cell).toInt())
        val ny = max(1, (rect.height() / cell).toInt())
        val unique = samples.filter { it.rssi in -126..-10 }
        if (unique.size < 2) return
        for (row in 0 until ny) for (col in 0 until nx) {
            val x = (col + 0.5f) / nx
            val y = (row + 0.5f) / ny
            var weightedSum = 0.0
            var total = 0.0
            var nearest = Double.MAX_VALUE
            for (s in unique) {
                val dx = (s.x - x).toDouble()
                val dy = (s.y - y).toDouble() * (rect.height() / rect.width()).toDouble()
                val d2 = dx * dx + dy * dy
                nearest = min(nearest, d2)
                val weight = 1.0 / (d2 + 0.001).pow(1.5)
                total += weight
                weightedSum += s.rssi * weight
            }
            // Do not invent confidence across large regions where nobody measured anything.
            if (sqrt(nearest) > 0.22) continue
            val estimate = (weightedSum / total).toInt()
            val color = strengthColor(estimate)
            val distanceAlpha = (1.0 - sqrt(nearest) / 0.22).coerceIn(0.0, 1.0)
            paint.color = Color.argb((90 + 75 * distanceAlpha).toInt(),
                Color.red(color), Color.green(color), Color.blue(color))
            val left = rect.left + rect.width() * col / nx
            val top = rect.top + rect.height() * row / ny
            canvas.drawRect(left, top, left + rect.width() / nx + 1f,
                top + rect.height() / ny + 1f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (rect.contains(event.x, event.y)) {
                    val x = ((event.x - rect.left) / rect.width()).coerceIn(0f, 1f)
                    val y = ((event.y - rect.top) / rect.height()).coerceIn(0f, 1f)
                    onMapTap?.invoke(x, y)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        fun strengthColor(rssi: Int): Int = when {
            rssi >= -55 -> Color.rgb(45, 212, 191)
            rssi >= -67 -> Color.rgb(132, 204, 22)
            rssi >= -75 -> Color.rgb(251, 191, 36)
            else -> Color.rgb(251, 113, 133)
        }

        fun decodeScaled(path: String?, maxDimension: Int = 2200): Bitmap? {
            if (path == null) return null
            return try {
                val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, dimensions)
                if (dimensions.outWidth < 1 || dimensions.outHeight < 1) return null
                var size = 1
                while (max(dimensions.outWidth, dimensions.outHeight) / size > maxDimension) size *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = size }
                BitmapFactory.decodeFile(path, options)
            } catch (_: Exception) { null }
        }
    }
}
