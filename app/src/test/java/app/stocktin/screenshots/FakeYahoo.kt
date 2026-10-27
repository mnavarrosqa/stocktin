package app.stocktin.screenshots

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.util.Random

/** Respuestas de Yahoo armadas para las capturas: siempre los mismos precios y gráficos. */
object FakeYahoo {

    private class Fake(val name: String, val price: Double, val percent: Double, val type: String = "EQUITY")

    private val known = mapOf(
        "SPY" to Fake("State Street SPDR S&P 500 ETF", 767.25, 0.26, "ETF"),
        "QQQ" to Fake("Invesco QQQ Trust, Series 1", 715.54, 0.47, "ETF"),
        "^GSPC" to Fake("S&P 500", 7699.92, 0.30, "INDEX"),
        "^NDX" to Fake("NASDAQ-100", 29423.48, 0.51, "INDEX"),
        "^IXIC" to Fake("NASDAQ Composite", 24120.11, 0.44, "INDEX"),
        "^DJI" to Fake("Dow Jones Industrial Average", 47012.66, -0.12, "INDEX"),
        "NVDA" to Fake("NVIDIA Corporation", 217.49, 2.39),
        "AAPL" to Fake("Apple Inc.", 344.73, -0.24),
        "BTC-USD" to Fake("Bitcoin USD", 79852.63, 0.36, "CRYPTOCURRENCY"),
        "^VIX" to Fake("CBOE Volatility Index", 17.88, -2.77, "INDEX"),
        "MELI" to Fake("MercadoLibre, Inc.", 2310.40, 1.84),
        "TSLA" to Fake("Tesla, Inc.", 452.10, -1.31),
    )

    private fun fake(symbol: String): Fake = known[symbol] ?: run {
        val r = Random(symbol.hashCode().toLong())
        Fake(symbol, 20 + r.nextDouble() * 400, (r.nextDouble() - 0.45) * 5)
    }

    private val now get() = System.currentTimeMillis() / 1000
    private val regularStart get() = now - 6 * 3600
    private val regularEnd get() = now + 30 * 60

    fun respond(url: String): String = when {
        "getcrumb" in url -> "fakecrumb"
        "fc.yahoo.com" in url -> ""
        "/v7/finance/quote" in url -> quotes(param(url, "symbols").split(','))
        "/v8/finance/chart/" in url -> chart(
            URLDecoder.decode(url.substringAfter("/v8/finance/chart/").substringBefore('?'), "UTF-8"),
            param(url, "range"),
        )
        "/v1/finance/trending" in url -> trending()
        "/v1/finance/screener" in url -> screener(param(url, "scrIds"))
        "/v1/finance/search" in url && "quotesCount=0" in url -> news(param(url, "q"))
        "/v1/finance/search" in url -> search(param(url, "q"))
        else -> throw IOException("Sin red en las capturas: $url")
    }

    private fun param(url: String, name: String): String =
        URLDecoder.decode(url.substringAfter("$name=", "").substringBefore('&'), "UTF-8")

    private fun quotes(symbols: List<String>): String {
        val result = JSONArray()
        symbols.forEach { symbol ->
            val f = fake(symbol)
            val previous = f.price / (1 + f.percent / 100)
            result.put(
                JSONObject()
                    .put("symbol", symbol)
                    .put("shortName", f.name)
                    .put("regularMarketPrice", f.price)
                    .put("regularMarketChange", f.price - previous)
                    .put("regularMarketChangePercent", f.percent)
                    .put("regularMarketPreviousClose", previous)
                    .put("regularMarketOpen", previous * 1.001)
                    .put("regularMarketDayHigh", f.price * 1.006)
                    .put("regularMarketDayLow", previous * 0.995)
                    .put("regularMarketVolume", 48_210_000)
                    .put("fiftyTwoWeekHigh", f.price * 1.12)
                    .put("fiftyTwoWeekLow", f.price * 0.71)
                    .put("marketCap", if (f.type == "EQUITY") f.price * 2.4e10 else 0)
                    .put("marketState", "REGULAR")
                    .put("regularMarketTime", now)
                    .put("currency", "USD")
                    .put("quoteType", f.type)
                    .put("fullExchangeName", if (f.type == "CRYPTOCURRENCY") "CCC" else "NasdaqGS"),
            )
        }
        return JSONObject().put("quoteResponse", JSONObject().put("result", result)).toString()
    }

    /** Un recorrido al azar (siempre el mismo para cada símbolo) que termina en el precio actual. */
    private fun chart(symbol: String, range: String): String {
        val f = fake(symbol)
        val previous = f.price / (1 + f.percent / 100)
        val (count, step) = when (range) {
            "1d" -> 72 to 300L
            "5d" -> 65 to 1800L
            "1mo" -> 22 to 86_400L
            "6mo", "ytd" -> 126 to 86_400L
            "1y" -> 250 to 86_400L
            else -> 260 to 604_800L
        }
        val start = if (range == "1d") regularStart else now - count * step
        val r = Random(symbol.hashCode().toLong() * 31 + range.hashCode())
        val first = if (range == "1d") previous else f.price * (0.7 + r.nextDouble() * 0.25)
        val walk = DoubleArray(count)
        var value = 0.0
        for (i in 0 until count) {
            value += r.nextGaussian()
            walk[i] = value
        }
        // Se estira el recorrido para que vaya del primer precio al actual.
        val times = JSONArray()
        val closes = JSONArray()
        for (i in 0 until count) {
            val t = i / (count - 1.0)
            val drift = first + (f.price - first) * t
            val noise = (walk[i] - walk.last() * t) * f.price * 0.0025
            times.put(start + i * step)
            closes.put(if (i == count - 1) f.price else drift + noise)
        }
        val meta = JSONObject()
            .put("symbol", symbol)
            .put("shortName", f.name)
            .put("currency", "USD")
            .put("instrumentType", f.type)
            .put("regularMarketPrice", f.price)
            .put("chartPreviousClose", if (range == "1d") previous else first)
            .put("previousClose", previous)
            .put("regularMarketTime", now)
            .put(
                "currentTradingPeriod",
                JSONObject().put("regular", JSONObject().put("start", regularStart).put("end", regularEnd)),
            )
        val result = JSONObject()
            .put("meta", meta)
            .put("timestamp", times)
            .put("indicators", JSONObject().put("quote", JSONArray().put(JSONObject().put("close", closes))))
        return JSONObject().put("chart", JSONObject().put("result", JSONArray().put(result))).toString()
    }

    /** Las que más se mueven: algunas inventadas, ordenadas como lo haría Yahoo. */
    private fun screener(id: String): String {
        val symbols = when (id) {
            "day_gainers" -> listOf("NVDA", "MELI", "QQQ", "SPY")
            "day_losers" -> listOf("TSLA", "AAPL", "^VIX")
            else -> listOf("NVDA", "TSLA", "AAPL", "SPY", "QQQ")
        }
        val quotes = JSONObject(quotes(symbols)).getJSONObject("quoteResponse").getJSONArray("result")
        return JSONObject().put("finance", JSONObject().put("result", JSONArray().put(JSONObject().put("quotes", quotes)))).toString()
    }

    private fun trending(): String {
        val quotes = JSONArray()
        listOf("NVDA", "TSLA", "MELI", "AAPL").forEach { quotes.put(JSONObject().put("symbol", it)) }
        return JSONObject().put("finance", JSONObject().put("result", JSONArray().put(JSONObject().put("quotes", quotes)))).toString()
    }

    private fun news(symbol: String): String {
        val titles = listOf(
            "$symbol sube tras resultados mejores a lo esperado y guía optimista para el próximo trimestre",
            "Los analistas elevan el precio objetivo de $symbol: qué esperan para 2027",
            "Wall Street abre en alza impulsado por las tecnológicas",
            "Cómo afecta la decisión de la Fed a las acciones de crecimiento",
        )
        val publishers = listOf("Reuters", "Bloomberg", "Yahoo Finance", "Barron's")
        val news = JSONArray()
        titles.forEachIndexed { i, title ->
            news.put(
                JSONObject()
                    .put("title", title)
                    .put("publisher", publishers[i])
                    .put("link", "https://finance.yahoo.com/")
                    .put("providerPublishTime", now - (i + 1) * 2700L),
            )
        }
        return JSONObject().put("news", news).toString()
    }

    private fun search(query: String): String {
        val quotes = JSONArray()
        known.filter { (symbol, f) -> symbol.contains(query, true) || f.name.contains(query, true) }
            .forEach { (symbol, f) ->
                quotes.put(JSONObject().put("symbol", symbol).put("shortname", f.name).put("exchDisp", "NASDAQ").put("typeDisp", "Equity"))
            }
        return JSONObject().put("quotes", quotes).toString()
    }
}
