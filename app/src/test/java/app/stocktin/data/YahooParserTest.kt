package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YahooParserTest {

    private val quoteJson = """
        {"quoteResponse":{"result":[
          {"symbol":"NVDA","shortName":"NVIDIA Corporation","regularMarketPrice":216.0,
           "regularMarketChange":5.05,"regularMarketChangePercent":2.39,"regularMarketPreviousClose":210.95,
           "marketState":"PRE","preMarketPrice":218.2,"preMarketChangePercent":1.02,
           "postMarketPrice":215.5,"postMarketTime":1761600000,"regularMarketTime":1761595200,
           "currency":"USD","quoteType":"EQUITY","fullExchangeName":"NasdaqGS","marketCap":5.2E12},
          {"symbol":"^NDX","shortName":"NASDAQ 100","regularMarketPrice":29273.4,
           "regularMarketChange":315.7,"regularMarketChangePercent":1.09,"marketState":"REGULAR",
           "regularMarketTime":1761595200},
          {"symbol":"SPY","shortName":"SPDR S&P 500","regularMarketPrice":765.37,
           "regularMarketChange":5.4,"regularMarketChangePercent":0.71,"marketState":"POST",
           "postMarketPrice":768.1,"postMarketChangePercent":0.36,"postMarketTime":1761600000,
           "regularMarketTime":1761595200}
        ],"error":null}}
    """.trimIndent()

    @Test
    fun `en pre-apertura se muestra el precio previo`() {
        val nvda = YahooParser.parseQuotes(quoteJson).first { it.symbol == "NVDA" }
        assertEquals("NVIDIA Corporation", nvda.name)
        assertEquals(216.0, nvda.price, 1e-9)
        assertEquals(2.39, nvda.changePercent, 1e-9)
        assertEquals(ExtSession.PRE, nvda.extSession)
        assertEquals(218.2, nvda.extPrice!!, 1e-9)
        assertEquals(1.02, nvda.extChangePercent!!, 1e-9)
        assertEquals(5.2e12, nvda.marketCap!!, 1.0)
    }

    @Test
    fun `despues del cierre se muestra el post`() {
        val spy = YahooParser.parseQuotes(quoteJson).first { it.symbol == "SPY" }
        assertEquals(ExtSession.POST, spy.extSession)
        assertEquals(0.36, spy.extChangePercent!!, 1e-9)
    }

    @Test
    fun `los indices no tienen fuera de horario`() {
        val ndx = YahooParser.parseQuotes(quoteJson).first { it.symbol == "^NDX" }
        assertNull(ndx.extSession)
        assertNull(ndx.extPrice)
        assertEquals(1.09, ndx.changePercent, 1e-9)
    }

    private val chartJson = """
        {"chart":{"result":[{"meta":{"currency":"USD","symbol":"QQQ","exchangeName":"NMS",
          "instrumentType":"ETF","regularMarketTime":1761595200,"regularMarketPrice":712.2,
          "chartPreviousClose":704.4,"previousClose":704.4,"shortName":"Invesco QQQ Trust",
          "currentTradingPeriod":{"pre":{"start":1761552000,"end":1761571800},
            "regular":{"start":1761571800,"end":1761595200},"post":{"start":1761595200,"end":1761609600}}},
          "timestamp":[1761560000,1761571800,1761580000,1761590000,1761596000],
          "indicators":{"quote":[{"close":[705.0,706.0,null,712.2,714.0]}]}}],"error":null}}
    """.trimIndent()

    @Test
    fun `el grafico saltea los huecos`() {
        val chart = YahooParser.parseChart(chartJson)
        assertEquals(listOf(705.0, 706.0, 712.2, 714.0), chart.prices)
        assertEquals(4, chart.timestamps.size)
        assertEquals("Invesco QQQ Trust", chart.meta.name)
    }

    @Test
    fun `el minigrafico usa solo el horario normal`() {
        val chart = YahooParser.parseChart(chartJson)
        assertEquals(listOf(706.0, 712.2), YahooParser.regularSession(chart))
    }

    @Test
    fun `sin cotizacion completa se arma con el grafico`() {
        val quote = YahooParser.quoteFromChart(YahooParser.parseChart(chartJson))
        assertEquals(712.2, quote.price, 1e-9)
        assertEquals(7.8, quote.change, 1e-9)
        assertEquals(7.8 / 704.4 * 100, quote.changePercent, 1e-9)
        assertEquals(ExtSession.POST, quote.extSession)
        assertEquals(714.0, quote.extPrice!!, 1e-9)
    }

    @Test
    fun `la busqueda descarta lo que no cotiza`() {
        val json = """
            {"quotes":[
              {"symbol":"AAPL","shortname":"Apple Inc.","exchDisp":"NASDAQ","typeDisp":"Equity","isYahooFinance":true},
              {"symbol":"XYZ","shortname":"Privada","isYahooFinance":false},
              {"symbol":"BTC-USD","longname":"Bitcoin USD","exchDisp":"CCC","typeDisp":"Cryptocurrency"}
            ]}
        """.trimIndent()
        val results = YahooParser.parseSearch(json)
        assertEquals(listOf("AAPL", "BTC-USD"), results.map { it.symbol })
        assertEquals("Bitcoin USD", results[1].name)
    }
}
