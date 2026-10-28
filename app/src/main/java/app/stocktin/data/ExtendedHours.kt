package app.stocktin.data

import java.time.Instant
import java.time.ZoneId

/** Un tramo fuera de horario para dibujar al costado del gráfico del día. */
data class ExtSegment(val prices: List<Double>, val label: String)

/** Pre-apertura y post-cierre alrededor de la rueda del gráfico de 1D. */
object ExtendedHours {

    private val NEW_YORK = ZoneId.of("America/New_York")

    /**
     * Los precios de [extended] (el gráfico con pre y post) que quedan antes y después de la rueda
     * de [regular]. Lo de antes es la pre-apertura; lo de después es el post-cierre del mismo día o,
     * si ya es otro día (a la mañana, antes de que abra), la pre-apertura de hoy.
     */
    fun split(regular: Chart, extended: Chart, maxGapSeconds: Long = 20 * 3600): Pair<ExtSegment?, ExtSegment?> {
        val first = regular.timestamps.firstOrNull() ?: return null to null
        val last = regular.timestamps.last()
        val before = extended.timestamps.indices.filter { extended.timestamps[it] in (first - maxGapSeconds) until first }
        val after = extended.timestamps.indices.filter { extended.timestamps[it] in (last + 1)..(last + maxGapSeconds) }
        fun day(t: Long) = Instant.ofEpochSecond(t).atZone(NEW_YORK).toLocalDate()
        val pre = before.takeIf { it.isNotEmpty() }?.let { idx -> ExtSegment(idx.map { extended.prices[it] }, "Pre") }
        val post = after.takeIf { it.isNotEmpty() }?.let { idx ->
            val sameDay = day(extended.timestamps[idx.first()]) == day(last)
            ExtSegment(idx.map { extended.prices[it] }, if (sameDay) "Post" else "Pre")
        }
        return pre to post
    }
}
