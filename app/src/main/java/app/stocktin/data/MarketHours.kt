package app.stocktin.data

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** Horario de la bolsa de EE.UU., contando pre-apertura (4:00) y post-cierre (hasta las 20:00). */
object MarketHours {

    private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    fun isUsActive(now: ZonedDateTime = ZonedDateTime.now(NEW_YORK)): Boolean {
        val ny = now.withZoneSameInstant(NEW_YORK)
        if (ny.dayOfWeek == DayOfWeek.SATURDAY || ny.dayOfWeek == DayOfWeek.SUNDAY) return false
        return ny.hour in 4 until 20
    }
}
