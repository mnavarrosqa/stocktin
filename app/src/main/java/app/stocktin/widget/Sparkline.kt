package app.stocktin.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/** Dibuja el minigráfico del día de los widgets. */
object Sparkline {

    /**
     * @param baseline cierre anterior: se dibuja como una línea punteada, y el gráfico la toma en
     * cuenta para que se vea si el precio está arriba o abajo.
     */
    fun render(
        prices: List<Double>,
        baseline: Double?,
        widthPx: Int,
        heightPx: Int,
        color: Int,
        baselineColor: Int,
        density: Float,
    ): Bitmap? {
        if (prices.size < 2 || widthPx <= 0 || heightPx <= 0) return null
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = 1.6f * density
        val pad = stroke
        var min = prices.min()
        var max = prices.max()
        if (baseline != null) {
            min = minOf(min, baseline)
            max = maxOf(max, baseline)
        }
        if (max - min < 1e-9) {
            max += 1
            min -= 1
        }
        val w = widthPx - 2 * pad
        val h = heightPx - 2 * pad
        fun x(i: Int) = pad + w * i / (prices.size - 1)
        fun y(v: Double) = (pad + h * (1 - (v - min) / (max - min))).toFloat()

        val line = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
        }
        val fill = Path(line).apply {
            lineTo(x(prices.lastIndex), heightPx.toFloat())
            lineTo(x(0), heightPx.toFloat())
            close()
        }
        canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, heightPx.toFloat(),
                ColorUtils.setAlphaComponent(color, 70), ColorUtils.setAlphaComponent(color, 0),
                Shader.TileMode.CLAMP,
            )
        })
        if (baseline != null) {
            val by = y(baseline)
            canvas.drawLine(0f, by, widthPx.toFloat(), by, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = baselineColor
                strokeWidth = density
                pathEffect = DashPathEffect(floatArrayOf(2 * density, 2 * density), 0f)
            })
        }
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        })
        return bitmap
    }

    /** Achica la serie para no dibujar más puntos que píxeles. */
    fun downsample(prices: List<Double>, max: Int): List<Double> {
        if (prices.size <= max || max < 2) return prices
        return List(max) { i -> prices[(i.toLong() * (prices.size - 1) / (max - 1)).toInt()] }
    }
}
