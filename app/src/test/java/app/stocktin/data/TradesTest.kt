package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TradesTest {

    private fun t(date: String, shares: Double, price: Double, buy: Boolean = true) = Trade(date + shares, "NVDA", date, shares, price, buy)

    @Test
    fun `precio promedio con varias compras`() {
        val s = Trades.summarize("NVDA", listOf(t("2026-03-01", 10.0, 100.0), t("2026-01-01", 10.0, 200.0)))
        assertEquals(20.0, s.holding!!.shares, 1e-9)
        assertEquals(150.0, s.holding!!.cost, 1e-9)
        assertEquals(0.0, s.realized, 1e-9)
    }

    @Test
    fun `una venta no cambia el promedio y realiza ganancia`() {
        val s = Trades.summarize("NVDA", listOf(t("2026-01-01", 10.0, 100.0), t("2026-02-01", 4.0, 130.0, buy = false), t("2026-03-01", 2.0, 160.0)))
        // Después de vender quedan 6 a 100; la compra a 160 los lleva a (600 + 320) / 8 = 115.
        assertEquals(8.0, s.holding!!.shares, 1e-9)
        assertEquals(115.0, s.holding!!.cost, 1e-9)
        assertEquals(120.0, s.realized, 1e-9)
    }

    @Test
    fun `vender todo deja la posicion en cero`() {
        val s = Trades.summarize("NVDA", listOf(t("2026-01-01", 5.0, 100.0), t("2026-02-01", 9.0, 90.0, buy = false)))
        assertNull(s.holding)
        assertEquals(-50.0, s.realized, 1e-9)
    }

    @Test
    fun `se guardan y se leen`() {
        val trades = listOf(t("2026-01-01", 5.0, 100.0), t("2026-02-01", 2.0, 90.0, buy = false))
        assertEquals(trades, Trades.fromJson(Trades.toJson(trades)))
    }
}
