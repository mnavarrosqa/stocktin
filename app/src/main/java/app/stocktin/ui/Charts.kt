package app.stocktin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.stocktin.data.ChartRange
import app.stocktin.data.ChartScale
import app.stocktin.data.ExtSegment
import app.stocktin.data.Format

/** Qué parte del ancho ocupa cada tramo fuera de horario. */
private const val EXT_SHARE = 0.14f

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
 * Gráfico grande del detalle. La línea y el relleno son verdes por encima de [baseline] (el cierre
 * anterior en 1D, o el primer precio del rango) y rojos por debajo, con una grilla suave de precios
 * redondos a la derecha y horas o fechas abajo. Al apoyar y deslizar el dedo, [onSelect] avisa qué
 * punto se está mirando (null al soltar).
 */
@Composable
fun PriceChart(
    prices: List<Double>,
    baseline: Double?,
    color: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** Otra serie (ya en la misma escala) para comparar, en una línea fina. */
    compare: List<Double>? = null,
    timestamps: List<Long> = emptyList(),
    range: ChartRange = ChartRange.DAY,
    /** En 1D, la pre-apertura (a la izquierda) y el post-cierre (a la derecha), en una línea aparte. */
    before: ExtSegment? = null,
    after: ExtSegment? = null,
) {
    val dash = MaterialColors.outline()
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val guide = MaterialColors.onSurfaceVariant()
    val compareColor = guide.copy(alpha = 0.8f)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = guide, fontFeatureSettings = "tnum")
    val axisStyle = labelStyle.copy(color = guide.copy(alpha = 0.75f))
    val extra = compare?.takeIf { it.size == prices.size }
    val base = baseline ?: prices.firstOrNull()
    // Los dos colores solo si la línea cruza la base; si no, el color de siempre.
    val up = if (base != null && prices.any { it > base }) Up else color
    val down = if (base != null && prices.any { it < base }) Down else color
    val outside = before?.prices.orEmpty() + after?.prices.orEmpty()
    var low = minOf(prices.minOrNull() ?: 0.0, extra?.minOrNull() ?: Double.MAX_VALUE, base ?: Double.MAX_VALUE, outside.minOrNull() ?: Double.MAX_VALUE)
    var high = maxOf(prices.maxOrNull() ?: 0.0, extra?.maxOrNull() ?: -Double.MAX_VALUE, base ?: -Double.MAX_VALUE, outside.maxOrNull() ?: -Double.MAX_VALUE)
    if (high - low < 1e-9) {
        high += 1
        low -= 1
    }
    val ticks = remember(low, high) { ChartScale.ticks(low, high) }
    val times = remember(timestamps, range) { if (timestamps.size == prices.size) ChartScale.timeTicks(timestamps, range) else emptyList() }
    val tickLabels = remember(ticks, labelStyle) { ticks.map { measurer.measure(Format.price(it), axisStyle) } }
    val timeLabels = remember(times, labelStyle) { times.map { (i, text) -> i to measurer.measure(text, axisStyle) } }
    // A la derecha, lugar para los precios de la grilla.
    val gutter = (tickLabels.maxOfOrNull { it.size.width } ?: 0)
    // Pre y post ocupan una franja a cada lado; la rueda, el resto.
    val beforeShare = if (before != null) EXT_SHARE else 0f
    val afterShare = if (after != null) EXT_SHARE else 0f
    val extLabels = remember(before?.label, after?.label, labelStyle) {
        listOfNotNull(before?.label, after?.label).associateWith { measurer.measure(it, axisStyle) }
    }
    val extColor = guide.copy(alpha = 0.75f)
    val band = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)

    Canvas(
        modifier.pointerInput(prices, gutter, beforeShare, afterShare) {
            if (prices.size < 2) return@pointerInput
            val plotWidth = size.width - gutter - 8.dp.toPx()
            val start = plotWidth * beforeShare
            val width = plotWidth * (1 - beforeShare - afterShare)
            fun index(x: Float) = (((x - start) / width) * (prices.size - 1)).toInt().coerceIn(0, prices.lastIndex)
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
        val stroke = 2.dp.toPx()
        val plotRight = size.width - gutter - 8.dp.toPx()
        val left = plotRight * beforeShare
        val right = plotRight * (1 - afterShare)
        val bottomAxis = if (timeLabels.isEmpty()) 0f else (timeLabels.first().second.size.height + 6.dp.toPx())
        val top = 18.dp.toPx()
        val bottom = size.height - bottomAxis - 16.dp.toPx()
        fun x(i: Int) = left + (right - left) * i / (prices.size - 1)
        fun y(v: Double) = (top + (bottom - top) * (1 - (v - low) / (high - low))).toFloat()

        // Grilla: líneas finas punteadas con el precio a la derecha.
        ticks.forEachIndexed { i, tick ->
            val ty = y(tick)
            drawLine(grid, Offset(0f, ty), Offset(plotRight, ty), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
            val label = tickLabels[i]
            drawText(label, topLeft = Offset(size.width - label.size.width, ty - label.size.height / 2f))
        }
        // Abajo, las horas o fechas, con una marquita.
        timeLabels.forEach { (i, label) ->
            val tx = x(i)
            drawLine(grid, Offset(tx, bottom + 8.dp.toPx()), Offset(tx, bottom + 12.dp.toPx()), strokeWidth = 1.dp.toPx())
            val lx = (tx - label.size.width / 2f).coerceIn(left, maxOf(left, right - label.size.width))
            drawText(label, topLeft = Offset(lx, size.height - label.size.height))
        }

        // Fuera de horario: una franja apenas sombreada, su nombre arriba y una línea gris punteada que
        // empalma con la rueda.
        fun extended(segment: ExtSegment, from: Float, to: Float, joinAt: Int, leading: Boolean) {
            drawRect(band, topLeft = Offset(from, 0f), size = androidx.compose.ui.geometry.Size(to - from, bottom))
            extLabels[segment.label]?.let { label ->
                drawText(label, topLeft = Offset((from + to) / 2f - label.size.width / 2f, 0f))
            }
            val points = segment.prices
            val path = Path()
            val n = points.size
            fun px(k: Int) = if (n == 1) (from + to) / 2f else from + (to - from) * k / (n - 1)
            if (leading) {
                path.moveTo(px(0), y(points[0]))
                for (k in 1 until n) path.lineTo(px(k), y(points[k]))
                path.lineTo(x(joinAt), y(prices[joinAt]))
            } else {
                path.moveTo(x(joinAt), y(prices[joinAt]))
                for (k in 0 until n) path.lineTo(px(k), y(points[k]))
            }
            drawPath(
                path, extColor,
                style = Stroke(
                    width = stroke * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                ),
            )
            if (!leading) drawCircle(extColor, radius = 3.dp.toPx(), center = Offset(px(n - 1), y(points.last())))
        }
        before?.let { extended(it, 0f, left - 4.dp.toPx(), joinAt = 0, leading = true) }
        after?.let { extended(it, right + 4.dp.toPx(), plotRight, joinAt = prices.lastIndex, leading = false) }

        val line = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
        }
        val by = base?.let(::y) ?: bottom
        // El relleno va de la línea hasta la base: arriba verde, abajo rojo, más intenso junto a la línea.
        val area = Path().apply {
            addPath(line)
            lineTo(x(prices.lastIndex), by)
            lineTo(x(0), by)
            close()
        }
        clipRect(left = left, top = 0f, right = right + stroke, bottom = by) {
            drawPath(area, Brush.verticalGradient(listOf(up.copy(alpha = 0.30f), up.copy(alpha = 0.03f)), startY = top, endY = by))
            drawPath(line, up, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        clipRect(left = left, top = by, right = right + stroke, bottom = size.height) {
            drawPath(area, Brush.verticalGradient(listOf(down.copy(alpha = 0.03f), down.copy(alpha = 0.30f)), startY = by, endY = bottom))
            drawPath(line, down, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (base != null) {
            drawLine(dash, Offset(0f, by), Offset(plotRight, by), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
        if (extra != null) {
            val other = Path().apply {
                moveTo(x(0), y(extra[0]))
                for (i in 1 until extra.size) lineTo(x(i), y(extra[i]))
            }
            drawPath(other, compareColor, style = Stroke(width = stroke * 0.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // El último precio: un punto con un halo.
        if (selected == null) {
            val last = Offset(x(prices.lastIndex), y(prices.last()))
            val lastColor = if (base != null && prices.last() < base) down else up
            drawCircle(lastColor.copy(alpha = 0.18f), radius = 9.dp.toPx(), center = last)
            drawCircle(lastColor, radius = 4.dp.toPx(), center = last)
        }
        if (selected != null && selected in prices.indices) {
            val sx = x(selected)
            val sy = y(prices[selected])
            val dot = if (base != null && prices[selected] < base) down else up
            drawLine(guide, Offset(sx, 0f), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            if (extra != null) drawCircle(compareColor, radius = 4.dp.toPx(), center = Offset(sx, y(extra[selected])))
            drawCircle(dot, radius = 6.dp.toPx(), center = Offset(sx, sy))
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(sx, sy))
        }
        if (selected == null && extra == null) {
            // Máximo y mínimo del rango, como en la app Bolsa de Apple.
            val hi = prices.indices.maxBy { prices[it] }
            val lo = prices.indices.minBy { prices[it] }
            listOf(hi to true, lo to false).distinctBy { it.first }.forEach { (index, above) ->
                val text = measurer.measure(Format.price(prices[index]), labelStyle)
                val p = Offset(x(index), y(prices[index]))
                val tx = (p.x - text.size.width / 2f).coerceIn(left, maxOf(left, right - text.size.width))
                val ty = if (above) p.y - text.size.height - 4.dp.toPx() else p.y + 4.dp.toPx()
                drawText(text, topLeft = Offset(tx, ty.coerceIn(0f, size.height - text.size.height)))
            }
        }
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
    labelSpace: Float = 0f,
    extra: List<Double>? = null,
    extraColor: Color = dash,
): (Int) -> Offset {
    var min = minOf(prices.min(), extra?.min() ?: Double.MAX_VALUE)
    var max = maxOf(prices.max(), extra?.max() ?: -Double.MAX_VALUE)
    if (baseline != null) {
        min = minOf(min, baseline)
        max = maxOf(max, baseline)
    }
    if (max - min < 1e-9) {
        max += 1
        min -= 1
    }
    // Lugar arriba y abajo para las etiquetas de máximo y mínimo.
    val pad = strokeWidth * 2 + labelSpace
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
    if (extra != null) {
        val other = Path().apply {
            moveTo(x(0), y(extra[0]))
            for (i in 1 until extra.size) lineTo(x(i), y(extra[i]))
        }
        drawPath(other, extraColor, style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawPath(line, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (selected != null && selected in prices.indices) {
        if (extra != null) drawCircle(extraColor, radius = 4.dp.toPx(), center = Offset(x(selected), y(extra[selected])))
        val sx = x(selected)
        drawLine(guide, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.dp.toPx())
        drawCircle(color, radius = 5.dp.toPx(), center = Offset(sx, y(prices[selected])))
        drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(sx, y(prices[selected])))
    }
    return { i -> Offset(x(i), y(prices[i])) }
}

private object MaterialColors {
    @Composable
    fun outline() = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    @Composable
    fun onSurfaceVariant() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
}
