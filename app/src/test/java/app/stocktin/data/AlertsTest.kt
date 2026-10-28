package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertsTest {

    private fun quote(symbol: String, price: Double) = Quote(symbol, symbol, price, 0.0, 0.0, null, null, null)

    @Test
    fun `suena al cruzar el precio para el lado que se pidio`() {
        val alerts = listOf(
            Alert("a", "NVDA", 150.0, above = true, created = 0),
            Alert("b", "NVDA", 120.0, above = false, created = 0),
            Alert("c", "AAPL", 200.0, above = true, created = 0),
        )
        val quotes = mapOf("NVDA" to quote("NVDA", 151.0), "AAPL" to quote("AAPL", 199.0))
        assertEquals(listOf("a"), Alerts.reached(alerts, quotes, "2026-10-28").map { it.first.id })
        assertEquals(listOf("b"), Alerts.reached(alerts, mapOf("NVDA" to quote("NVDA", 120.0)), "2026-10-28").map { it.first.id })
        assertEquals(emptyList<String>(), Alerts.reached(alerts, emptyMap(), "2026-10-28").map { it.first.id })
    }

    @Test
    fun `movimiento del dia suena una vez por dia y solo con cotizacion de hoy`() {
        val utc = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 10, 28)
        val noon = today.atStartOfDay(utc).plusHours(15).toEpochSecond()
        fun q(pct: Double, time: Long) = Quote("NVDA", "NVDA", 100.0, pct, pct, null, null, null, time = time)
        val alert = Alert("m", "NVDA", 5.0, above = true, created = 0, kind = AlertKind.DAY_MOVE)
        assertEquals(1, Alerts.reached(listOf(alert), mapOf("NVDA" to q(-5.5, noon)), "2026-10-28", utc).size)
        assertEquals(0, Alerts.reached(listOf(alert), mapOf("NVDA" to q(4.0, noon)), "2026-10-28", utc).size)
        assertEquals(0, Alerts.reached(listOf(alert.copy(lastFired = "2026-10-28")), mapOf("NVDA" to q(8.0, noon)), "2026-10-28", utc).size)
        // El cambio de ayer, con la bolsa todavía cerrada, no cuenta.
        assertEquals(0, Alerts.reached(listOf(alert), mapOf("NVDA" to q(8.0, noon - 86_400)), "2026-10-28", utc).size)
    }

    @Test
    fun `lee precios escritos de varias formas`() {
        assertEquals(150.0, Alerts.parsePrice("150")!!, 1e-9)
        assertEquals(150.5, Alerts.parsePrice("150.5")!!, 1e-9)
        assertEquals(150.5, Alerts.parsePrice(" 150,5 ")!!, 1e-9)
        assertEquals(1234.5, Alerts.parsePrice("1.234,5")!!, 1e-9)
        assertEquals(1234.5, Alerts.parsePrice("$1,234.5")!!, 1e-9)
        assertNull(Alerts.parsePrice(""))
        assertNull(Alerts.parsePrice("abc"))
        assertNull(Alerts.parsePrice("0"))
    }

    @Test
    fun `describe contra el precio de ahora`() {
        assertEquals("Sube a 110.00 (+10.00%)", Alerts.describe(110.0, 100.0))
        assertEquals("Baja a 95.00 (-5.00%)", Alerts.describe(95.0, 100.0))
    }

    @Test
    fun `se guardan y se leen`() {
        val alerts = listOf(
            Alert("a", "BTC-USD", 70000.0, above = true, created = 5),
            Alert("b", "NVDA", 5.0, above = true, created = 6, kind = AlertKind.DAY_MOVE, lastFired = "2026-10-27"),
        )
        assertEquals(alerts, Alerts.fromJson(Alerts.toJson(alerts)))
    }
}
