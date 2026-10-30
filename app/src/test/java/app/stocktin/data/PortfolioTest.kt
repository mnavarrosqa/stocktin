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

class PortfolioAllocationTest {

    private fun q(symbol: String, price: Double, currency: String = "USD") = Quote(symbol, symbol, price, 0.0, 0.0, null, currency, null)

    @Test
    fun `reparto de la cartera por moneda y con otros`() {
        val holdings = listOf(Holding("A", 6.0, 1.0), Holding("B", 3.0, 1.0), Holding("C", 1.0, 1.0), Holding("ARS", 100.0, 1.0))
        val quotes = mapOf("A" to q("A", 10.0), "B" to q("B", 10.0), "C" to q("C", 10.0), "ARS" to q("ARS", 10.0, "ARS"))
        assertEquals(listOf("A" to 0.6, "B" to 0.3, "C" to 0.1), Portfolio.allocation(holdings, quotes, "USD"))
        val grouped = Portfolio.allocation(holdings, quotes, "USD", max = 2)
        assertEquals(listOf("A", "Otros"), grouped.map { it.first })
        assertEquals(0.4, grouped[1].second, 1e-9)
        assertEquals(0.3, Portfolio.weight("B", holdings, quotes)!!, 1e-9)
        // Solo en su moneda, y si es lo único que hay no tiene sentido.
        assertEquals(null, Portfolio.weight("ARS", holdings, quotes))
    }
}

class MistypedPesosTest {

    private fun q(price: Double, currency: String) = Quote("X", "X", price, 0.0, 0.0, null, currency, null)

    @Test
    fun `corrige el punto de miles mal leido en pesos`() {
        assertEquals(19990.0, Portfolio.fixMistypedPesos(Holding("MSFT.BA", 5.0, 19.99), q(28880.0, "ARS"))!!.cost, 1e-6)
        assertEquals(8060.0, Portfolio.fixMistypedPesos(Holding("GOOGL.BA", 124.0, 8.06), q(8040.0, "ARS"))!!.cost, 1e-6)
        // Un promedio razonable, en dólares o sin cotización no se toca.
        assertEquals(null, Portfolio.fixMistypedPesos(Holding("MSFT.BA", 5.0, 19990.0), q(28880.0, "ARS")))
        assertEquals(null, Portfolio.fixMistypedPesos(Holding("MSFT", 5.0, 0.5), q(500.0, "USD")))
        assertEquals(null, Portfolio.fixMistypedPesos(Holding("MSFT.BA", 5.0, 19.99), null))
    }
}
