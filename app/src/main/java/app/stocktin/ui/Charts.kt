package app.stocktin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Minigráfico de una fila de la lista. */
@Composable
fun Sparkline(prices: List<Double>, baseline: Double?, color: Color, modifier: Modifier = Modifier) {
    val dash = MaterialColors.outline()
    Canvas(modifier) {
        if (prices.size < 2) return@Canvas
        drawSeries(prices, baseline, color, dash, strokeWidth = 1.5.dp.toPx(), selected = null)
    }
}

/**
 * Gráfico grande del detalle. Al apoyar y deslizar el dedo, [onSelect] avisa qué punto se está
 * mirando (null al soltar).
 */
@Composable
fun PriceChart(
    prices: List<Double>,
    baseline: Double?,
    color: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dash = MaterialColors.outline()
    val guide = MaterialColors.onSurfaceVariant()
    Canvas(
        modifier.pointerInput(prices) {
            if (prices.size < 2) return@pointerInput
            fun index(x: Float) = ((x / size.width) * (prices.size - 1)).toInt().coerceIn(0, prices.lastIndex)
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                onSelect(index(down.position.x))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    change.consume()
                    onSelect(index(change.position.x))
                }
                onSelect(null)
            }
        },
    ) {
        if (prices.size < 2) return@Canvas
        drawSeries(prices, baseline, color, dash, strokeWidth = 2.dp.toPx(), selected = selected, guide = guide)
    }
}

private fun DrawScope.drawSeries(
    prices: List<Double>,
    baseline: Double?,
    color: Color,
    dash: Color,
    strokeWidth: Float,
    selected: Int?,
    guide: Color = dash,
) {
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
    val pad = strokeWidth * 2
    val w = size.width
    val h = size.height - 2 * pad
    fun x(i: Int) = w * i / (prices.size - 1)
    fun y(v: Double) = (pad + h * (1 - (v - min) / (max - min))).toFloat()

    val line = Path().apply {
        moveTo(x(0), y(prices[0]))
        for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(x(prices.lastIndex), size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
    if (baseline != null) {
        val by = y(baseline)
        drawLine(
            dash,
            Offset(0f, by),
            Offset(w, by),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
    }
    drawPath(line, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (selected != null && selected in prices.indices) {
        val sx = x(selected)
        drawLine(guide, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.dp.toPx())
        drawCircle(color, radius = 5.dp.toPx(), center = Offset(sx, y(prices[selected])))
        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(sx, y(prices[selected])))
    }
}

private object MaterialColors {
    @Composable
    fun outline() = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    @Composable
    fun onSurfaceVariant() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
}
