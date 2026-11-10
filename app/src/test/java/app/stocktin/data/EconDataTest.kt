package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class EconDataTest {

    private fun monthly(from: LocalDate, values: List<Double>) = values.mapIndexed { i, v -> from.plusMonths(i.toLong()) to v }

    @Test
    fun `lee el csv de FRED con datos que faltan`() {
        val csv = "observation_date,CPIAUCSL\n2026-08-01,320.5\n2026-09-01,.\n2026-10-01,321.4\n"
        assertEquals(listOf(LocalDate.of(2026, 8, 1) to 320.5, LocalDate.of(2026, 10, 1) to 321.4), EconData.parseCsv(csv))
    }

    @Test
    fun `inflacion anual, mensual y nucleo`() {
        // 25 meses: el índice sube 0.25% por mes.
        val all = monthly(LocalDate.of(2024, 10, 1), (0..24).map { 300.0 * Math.pow(1.0025, it.toDouble()) })
        val core = monthly(LocalDate.of(2024, 10, 1), (0..24).map { 310.0 * Math.pow(1.003, it.toDouble()) })
        val r = EconData.cpi(all, core)!!
        assertEquals("3.0% anual", r.headline)
        assertEquals("2026-10", r.period)
        assertEquals("Octubre 2026", r.periodLabel)
        assertEquals("+0.2%", r.details.toMap()["Mensual"])
        assertEquals("3.7%", r.details.toMap()["Núcleo (anual)"])
        assertEquals(12, r.history.size)
    }

    @Test
    fun `empleos creados y desempleo`() {
        val payrolls = monthly(LocalDate.of(2026, 7, 1), listOf(159000.0, 159120.0, 159300.0, 159450.0))
        val rate = monthly(LocalDate.of(2026, 7, 1), listOf(4.1, 4.2, 4.2, 4.3))
        val r = EconData.jobs(payrolls, rate)!!
        assertEquals("+150 mil empleos", r.headline)
        assertEquals("4.3%", r.details.toMap()["Desempleo"])
        assertEquals("+180 mil empleos", r.details.toMap()["Mes anterior"])
    }

    @Test
    fun `pbi del trimestre`() {
        val g = listOf(LocalDate.of(2026, 4, 1) to 3.0, LocalDate.of(2026, 7, 1) to 2.8)
        val r = EconData.gdp(g)!!
        assertEquals("+2.8% anualizado", r.headline)
        assertEquals("3.er trimestre 2026", r.periodLabel)
        assertEquals("2026-07", r.period)
    }

    @Test
    fun `tasa de la fed y su ultimo cambio`() {
        val days = (0..400).map { LocalDate.of(2025, 10, 1).plusDays(it.toLong()) }
        val upper = days.map { it to if (it < LocalDate.of(2026, 9, 17)) 4.50 else 4.25 }
        val lower = upper.map { it.first to it.second - 0.25 }
        val r = EconData.fed(lower, upper)!!
        assertEquals("4.00% – 4.25%", r.headline)
        assertEquals("Bajó 0.25 el 17 de septiembre 2026", r.details.toMap()["Último cambio"])
    }

    @Test
    fun `el resultado corresponde al evento`() {
        val ny = ZoneId.of("America/New_York")
        val cpiEvent = AgendaEvent(ZonedDateTime.of(2026, 11, 10, 8, 30, 0, 0, ny).toInstant().toEpochMilli(), AgendaKind.CPI, "CPI", period = "2026-10")
        val october = EconResult(AgendaKind.CPI, "3.0% anual", "Octubre 2026", "2026-10", emptyList(), emptyList(), "", "3.0% anual")
        assertTrue(EconData.matches(cpiEvent, october))
        assertFalse(EconData.matches(cpiEvent, october.copy(period = "2026-09")))
        val fedEvent = AgendaEvent(ZonedDateTime.of(2026, 12, 9, 14, 0, 0, 0, ny).toInstant().toEpochMilli(), AgendaKind.FED, "Fed")
        val fed = EconResult(AgendaKind.FED, "", "", "2026-12", emptyList(), emptyList(), "", "", asOf = "2026-12-08")
        assertFalse(EconData.matches(fedEvent, fed))
        assertTrue(EconData.matches(fedEvent, fed.copy(asOf = "2026-12-10")))
    }
}
