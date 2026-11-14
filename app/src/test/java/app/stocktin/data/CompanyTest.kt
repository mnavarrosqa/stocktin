package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanyTest {

    @Test
    fun `perfil y balances de quoteSummary`() {
        val body = """{"quoteSummary":{"result":[{
            "assetProfile":{"sector":"Technology","industry":"Semiconductors","longBusinessSummary":"Hace chips.","website":"https://nvidia.com","fullTimeEmployees":36000},
            "earningsHistory":{"history":[
              {"epsActual":{"raw":0.81,"fmt":"0.81"},"epsEstimate":{"raw":0.75},"surprisePercent":{"raw":0.08},"quarter":{"raw":1751241600},"period":"-2q"},
              {"epsActual":{"raw":0.92},"epsEstimate":{"raw":0.95},"quarter":{"raw":1759190400},"period":"-1q"}
            ]}}]}}"""
        val info = Company.parse(body)!!
        assertEquals("Technology", info.sector)
        assertEquals(36000L, info.employees)
        assertEquals(2, info.earnings.size)
        assertEquals("T2 2025", info.earnings[0].quarter)
        assertEquals(8.0, info.earnings[0].surprise!!, 1e-9)
        // Sin sorpresa informada, se calcula.
        assertEquals("T3 2025", info.earnings[1].quarter)
        assertEquals(-3.157, info.earnings[1].surprise!!, 0.01)
    }

    @Test
    fun `sin resultado no hay info`() {
        assertNull(Company.parse("""{"quoteSummary":{"result":[]}}"""))
    }

    @Test
    fun `fundamentales de la cotizacion`() {
        val body = """{"quoteResponse":{"result":[{"symbol":"KO","regularMarketPrice":70.0,"trailingPE":24.5,
            "epsTrailingTwelveMonths":2.86,"trailingAnnualDividendYield":0.028,"fiftyDayAverage":69.1,"twoHundredDayAverage":66.4}]}}"""
        val q = YahooParser.parseQuotes(body).single()
        assertEquals(24.5, q.pe!!, 1e-9)
        assertEquals(2.8, q.dividendYield!!, 1e-9)
        assertEquals(66.4, q.avg200!!, 1e-9)
        assertEquals(q, Storage.quotesFromJson(Storage.quotesToJson(listOf(q))).single())
    }
}
