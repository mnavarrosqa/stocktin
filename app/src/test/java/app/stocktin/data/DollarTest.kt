package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DollarTest {

    private fun q(symbol: String, price: Double, previous: Double = price) = symbol to Quote(symbol, symbol, price, price - previous, 0.0, previous, null, null)

    @Test
    fun `ccl como mediana de los pares y brecha con el oficial`() {
        val quotes = mapOf(
            q("ARS=X", 1400.0),
            q("GGAL.BA", 7000.0, 6900.0), q("GGAL", 46.0, 46.0),   // 7000*10/46 = 1521.7
            q("YPFD.BA", 50000.0), q("YPF", 33.0),                 // 1515.2
            q("PAMP.BA", 3000.0), q("PAM", 49.0),                  // 1530.6
        )
        val r = Dollar.rates(quotes)
        assertEquals(1521.74, r.ccl!!, 0.01)
        assertEquals(1400.0, r.official!!, 1e-9)
        assertEquals(8.70, r.gap!!, 0.01)
    }

    @Test
    fun `sin datos no hay ccl`() {
        assertNull(Dollar.rates(emptyMap()).ccl)
    }

    @Test
    fun `historia de la cartera por dia y moneda`() {
        var h = PortfolioHistory.record(emptyMap(), "2026-11-13", mapOf("USD" to 100.0))
        h = PortfolioHistory.record(h, "2026-11-14", mapOf("USD" to 110.0, "ARS" to 5000.0))
        h = PortfolioHistory.record(h, "2026-11-14", mapOf("USD" to 112.0, "ARS" to 5100.0))
        assertEquals(listOf("2026-11-13" to 100.0, "2026-11-14" to 112.0), PortfolioHistory.series(h, "USD"))
        assertEquals(listOf("2026-11-14" to 5100.0), PortfolioHistory.series(h, "ARS"))
        assertEquals(h, PortfolioHistory.fromJson(PortfolioHistory.toJson(h)))
        // Lo de hace más de 5 años se va.
        assertEquals(1, PortfolioHistory.record(mapOf("2020-01-01" to mapOf("USD" to 1.0)), "2026-11-14", mapOf("USD" to 2.0)).size)
    }
}
