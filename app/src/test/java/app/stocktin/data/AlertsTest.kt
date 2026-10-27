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
        assertEquals(listOf("a"), Alerts.reached(alerts, quotes).map { it.first.id })
        assertEquals(listOf("b"), Alerts.reached(alerts, mapOf("NVDA" to quote("NVDA", 120.0))).map { it.first.id })
        assertEquals(emptyList<String>(), Alerts.reached(alerts, emptyMap()).map { it.first.id })
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
        val alerts = listOf(Alert("a", "BTC-USD", 70000.0, above = true, created = 5))
        assertEquals(alerts, Alerts.fromJson(Alerts.toJson(alerts)))
    }
}
