package dev.wifiwalk.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Small signal-strength trend chart; recorded RSSI, not predicted performance. */
class SignalChartView(context: Context, private val monitor: WifiMonitor) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private fun dp(x: Float) = x * d

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(45f)
        val top = dp(12f)
        val right = width - dp(12f)
        val bottom = height - dp(22f)
        if (right <= left || bottom <= top) return
        val minDb = -100f
        val maxDb = -30f
        fun yOf(rssi: Int) = bottom - ((rssi.toFloat() - minDb) / (maxDb - minDb)).coerceIn(0f, 1f) * (bottom - top)
        for (db in listOf(-30, -50, -70, -90)) {
            val yy = yOf(db)
            p.color = Color.argb(65, 140, 165, 187)
            p.strokeWidth = dp(1f)
            canvas.drawLine(left, yy, right, yy, p)
            p.color = Color.rgb(161, 179, 199)
            p.textSize = dp(10f)
            p.textAlign = Paint.Align.LEFT
            canvas.drawText("$db", dp(3f), yy + dp(3f), p)
        }
        val history = monitor.points.takeLast(90)
        if (history.isEmpty()) {
            p.color = Color.rgb(157, 178, 201)
            p.textSize = dp(13f)
            canvas.drawText("Waiting for live signal...", left + dp(8f), (bottom + top) / 2f, p)
            return
        }
        val line = Path()
        val fill = Path()
        val n = history.size
        val duration = 90f
        history.forEachIndexed { index, point ->
            val x = right - ((n - 1 - index) / duration) * (right - left)
            val y = yOf(point.rssi)
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, bottom)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(right, bottom)
        fill.close()
        p.style = Paint.Style.FILL
        p.color = Color.argb(38, 43, 212, 191)
        canvas.drawPath(fill, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = Color.rgb(45, 212, 191)
        canvas.drawPath(line, p)
        p.style = Paint.Style.FILL
        val current = history.last()
        canvas.drawCircle(right, yOf(current.rssi), dp(4f), p)
        p.textSize = dp(10f)
        p.color = Color.rgb(165, 181, 200)
        p.textAlign = Paint.Align.RIGHT
        canvas.drawText("Recent foreground measurements (last ~2 min)", right, height - dp(4f), p)
    }
}
