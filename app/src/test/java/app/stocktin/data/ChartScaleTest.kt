package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChartScaleTest {

    @Test
    fun `precios redondos para la grilla`() {
        assertEquals(listOf(212.0, 214.0, 216.0, 218.0), ChartScale.ticks(211.35, 218.79))
        assertEquals(listOf(7000.0, 7500.0), ChartScale.ticks(6900.0, 7700.0, target = 2))
        assertEquals(listOf(0.1, 0.2, 0.3), ChartScale.ticks(0.06, 0.34, target = 3).map { Math.round(it * 10) / 10.0 })
        assertTrue(ChartScale.ticks(5.0, 5.0).isEmpty())
    }

    @Test
    fun `horas en punto en el dia y meses en el anio`() {
        val ny = ZoneId.of("America/New_York")
        val open = ZonedDateTime.of(2026, 10, 28, 9, 30, 0, 0, ny)
        val day = (0 until 78).map { open.plusMinutes(5L * it).toEpochSecond() }
        val ticks = ChartScale.timeTicks(day, ChartRange.DAY, ny)
        assertTrue(ticks.size in 2..4)
        assertTrue(ticks.all { it.second.endsWith(":00") })

        val year = (0 until 250).map { open.minusDays(365).plusDays(it * 365L / 250).toEpochSecond() }
        val months = ChartScale.timeTicks(year, ChartRange.YEAR, ny)
        assertTrue(months.size in 2..4)
        assertTrue(months.zipWithNext().all { (a, b) -> a.first < b.first })
    }
}
