package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioTest {

    private fun quote(symbol: String, price: Double, change: Double, currency: String) =
        Quote(symbol, symbol, price, change, 0.0, price - change, currency, null)

    @Test
    fun `valor, cambio de hoy y ganancia de una posicion`() {
        val p = Portfolio.position(Holding("NVDA", 10.0, 100.0), quote("NVDA", 120.0, 2.0, "USD"))
        assertEquals(1200.0, p.value, 1e-9)
        assertEquals(20.0, p.dayChange, 1e-9)
        assertEquals(200.0, p.gain, 1e-9)
        assertEquals(20.0, p.gainPercent, 1e-9)
        assertEquals(20.0 / 1180.0 * 100, p.dayPercent, 1e-9)
    }

    @Test
    fun `totales separados por moneda y sin los que no tienen cotizacion`() {
        val holdings = listOf(
            Holding("NVDA", 10.0, 100.0),
            Holding("AAPL", 1.0, 200.0),
            Holding("GGAL.BA", 100.0, 5000.0),
            Holding("SINDATOS", 5.0, 1.0),
        )
        val quotes = mapOf(
            "NVDA" to quote("NVDA", 120.0, 2.0, "USD"),
            "AAPL" to quote("AAPL", 180.0, -1.0, "USD"),
            "GGAL.BA" to quote("GGAL.BA", 6000.0, 100.0, "ARS"),
        )
        val totals = Portfolio.totals(holdings, quotes)
        assertEquals(listOf("ARS", "USD"), totals.map { it.currency })
        val usd = totals.single { it.currency == "USD" }
        assertEquals(2, usd.count)
        assertEquals(1380.0, usd.position.value, 1e-9)
        assertEquals(19.0, usd.position.dayChange, 1e-9)
        assertEquals(180.0, usd.position.gain, 1e-9)
    }

    @Test
    fun `se guarda y se lee`() {
        val holdings = listOf(Holding("BTC-USD", 0.015, 61000.5))
        assertEquals(holdings, Portfolio.fromJson(Portfolio.toJson(holdings)))
    }
}
