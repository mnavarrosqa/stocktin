package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CedearsTest {

    private fun q(symbol: String, type: String, currency: String = "USD") = Quote(symbol, symbol, 1.0, 0.0, 0.0, null, currency, null, quoteType = type)

    @Test
    fun `cedears de acciones y etfs de EE UU`() {
        assertEquals(LocalListing("AAPL.BA", "CEDEARs"), Cedears.localFor("AAPL", q("AAPL", "EQUITY")))
        assertEquals("SPY.BA", Cedears.localFor("SPY", q("SPY", "ETF"))?.symbol)
        assertEquals("BRKB.BA", Cedears.localFor("BRK-B", q("BRK-B", "EQUITY"))?.symbol)
        // Sin cotización todavía, se ofrece igual.
        assertEquals("NVDA.BA", Cedears.localFor("NVDA", null)?.symbol)
    }

    @Test
    fun `ADRs argentinos van a la accion local`() {
        assertEquals(LocalListing("YPFD.BA", "Acciones en pesos"), Cedears.localFor("YPF", q("YPF", "EQUITY")))
    }

    @Test
    fun `nada para indices, cripto, monedas, futuros u otras bolsas`() {
        assertNull(Cedears.localFor("^GSPC", q("^GSPC", "INDEX")))
        assertNull(Cedears.localFor("BTC-USD", q("BTC-USD", "CRYPTOCURRENCY")))
        assertNull(Cedears.localFor("GC=F", q("GC=F", "FUTURE")))
        assertNull(Cedears.localFor("GGAL.BA", q("GGAL.BA", "EQUITY", "ARS")))
        assertNull(Cedears.localFor("SAP", q("SAP", "EQUITY", "EUR")))
    }
}
