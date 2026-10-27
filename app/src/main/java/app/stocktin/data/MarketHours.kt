package app.stocktin.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Horario de la bolsa de EE.UU.: pre-apertura desde las 4:00, rueda de 9:30 a 16:00 y post-cierre hasta las 20:00. */
object MarketHours {

    private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    private val PRE_OPEN = LocalTime.of(4, 0)
    private val OPEN = LocalTime.of(9, 30)
    private val CLOSE = LocalTime.of(16, 0)
    private val POST_CLOSE = LocalTime.of(20, 0)

    enum class Phase { PRE, OPEN, POST, CLOSED }

    /** En qué parte del día está la bolsa y cuándo es el próximo cambio (apertura o cierre de la rueda). */
    data class Status(val phase: Phase, val next: ZonedDateTime) {
        val opensNext: Boolean get() = phase != Phase.OPEN
    }

    fun isUsActive(now: ZonedDateTime = ZonedDateTime.now(NEW_YORK)): Boolean = status(now).phase != Phase.CLOSED

    /** No cuenta los feriados de EE.UU. (para eso está el estado que manda Yahoo). */
    fun status(now: ZonedDateTime = ZonedDateTime.now(NEW_YORK)): Status {
        val ny = now.withZoneSameInstant(NEW_YORK)
        val weekday = ny.dayOfWeek != DayOfWeek.SATURDAY && ny.dayOfWeek != DayOfWeek.SUNDAY
        val time = ny.toLocalTime()
        val phase = when {
            !weekday -> Phase.CLOSED
            time < PRE_OPEN -> Phase.CLOSED
            time < OPEN -> Phase.PRE
            time < CLOSE -> Phase.OPEN
            time < POST_CLOSE -> Phase.POST
            else -> Phase.CLOSED
        }
        val next = if (phase == Phase.OPEN) {
            ny.with(CLOSE)
        } else {
            // La próxima apertura: hoy si todavía no abrió, si no el siguiente día hábil.
            var day = if (weekday && time < OPEN) ny.toLocalDate() else ny.toLocalDate().plusDays(1)
            while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.plusDays(1)
            day.atTime(OPEN).atZone(NEW_YORK)
        }
        return Status(phase, next.withSecond(0).withNano(0))
    }

    /** "3 h 12 min", "45 min" o "1 min". */
    fun countdown(from: ZonedDateTime, to: ZonedDateTime): String {
        val minutes = Duration.between(from, to).toMinutes().coerceAtLeast(1)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }
}
