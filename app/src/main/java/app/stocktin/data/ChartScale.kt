package app.stocktin.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Las marcas de los ejes del gráfico grande: precios redondos y horas o fechas. */
object ChartScale {

    /**
     * Unos [target] precios redondos (1, 2, 2.5 o 5 por una potencia de 10) dentro de [min]..[max],
     * para las líneas de la grilla.
     */
    fun ticks(min: Double, max: Double, target: Int = 4): List<Double> {
        if (!(max > min)) return emptyList()
        val raw = (max - min) / target
        val magnitude = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
        val first = ceil(min / step) * step
        return generateSequence(first) { it + step }
            .takeWhile { it <= max + step * 1e-9 }
            .map { if (abs(it) < step * 1e-9) 0.0 else it }
            .toList()
    }

    /**
     * Dónde poner las etiquetas de abajo: en los cambios de hora (1D), de día (5D), de semana (1M),
     * de mes o de año, según el rango, y como mucho [max]. Devuelve el índice del punto y el texto.
     */
    fun timeTicks(
        timestamps: List<Long>,
        range: ChartRange,
        zone: ZoneId = ZoneId.systemDefault(),
        max: Int = 4,
    ): List<Pair<Int, String>> {
        if (timestamps.size < 3) return emptyList()
        val times = timestamps.map { Instant.ofEpochSecond(it).atZone(zone) }
        fun unit(t: ZonedDateTime): Int = when (range) {
            ChartRange.DAY -> t.hour
            ChartRange.WEEK -> t.dayOfYear
            ChartRange.MONTH -> t.get(ChronoField.ALIGNED_WEEK_OF_YEAR)
            ChartRange.SIX_MONTHS, ChartRange.YTD -> t.monthValue
            ChartRange.YEAR -> (t.monthValue - 1) / 3 + t.year * 4
            ChartRange.FIVE_YEARS, ChartRange.MAX -> t.year
        }
        val pattern = when (range) {
            ChartRange.DAY -> "H:mm"
            ChartRange.WEEK -> "EEE"
            ChartRange.MONTH -> "d MMM"
            ChartRange.SIX_MONTHS, ChartRange.YTD, ChartRange.YEAR -> "MMM"
            ChartRange.FIVE_YEARS, ChartRange.MAX -> "yyyy"
        }
        val format = DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-AR"))
        var candidates = (1 until times.size).filter { unit(times[it]) != unit(times[it - 1]) }
        // Con muchos cambios (por ejemplo, 20 años en Máx), uno cada tanto.
        if (candidates.size > max) {
            val every = ceil(candidates.size / max.toDouble()).toInt()
            candidates = candidates.filterIndexed { i, _ -> i % every == every - 1 }
        }
        return candidates.map { i ->
            // En 1D, la hora en punto (el primer dato de la hora puede ser 10:05).
            val t = if (range == ChartRange.DAY) times[i].withMinute(0) else times[i]
            i to format.format(t).replace(".", "").replaceFirstChar { it.uppercase() }
        }
    }
}
