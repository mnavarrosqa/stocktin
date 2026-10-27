package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/** Convierte las respuestas de Yahoo Finance en modelos. No usa nada de Android para poder testearlo. */
object YahooParser {

    /** Respuesta de `v8/finance/chart`. */
    fun parseChart(body: String): Chart {
        val chart = JSONObject(body).getJSONObject("chart")
        val result = chart.optJSONArray("result")?.optJSONObject(0)
            ?: error(chart.optJSONObject("error")?.optString("description") ?: "Sin datos")
        val metaJson = result.getJSONObject("meta")
        val periods = metaJson.optJSONObject("currentTradingPeriod")
        val regular = periods?.optJSONObject("regular")
        val meta = ChartMeta(
            symbol = metaJson.getString("symbol"),
            name = metaJson.optStringOrNull("shortName") ?: metaJson.optStringOrNull("longName"),
            price = metaJson.optDoubleOrNull("regularMarketPrice"),
            previousClose = metaJson.optDoubleOrNull("previousClose") ?: metaJson.optDoubleOrNull("chartPreviousClose"),
            currency = metaJson.optStringOrNull("currency"),
            exchange = metaJson.optStringOrNull("fullExchangeName") ?: metaJson.optStringOrNull("exchangeName"),
            quoteType = metaJson.optStringOrNull("instrumentType"),
            dayHigh = metaJson.optDoubleOrNull("regularMarketDayHigh"),
            dayLow = metaJson.optDoubleOrNull("regularMarketDayLow"),
            volume = metaJson.optDoubleOrNull("regularMarketVolume")?.toLong(),
            yearHigh = metaJson.optDoubleOrNull("fiftyTwoWeekHigh"),
            yearLow = metaJson.optDoubleOrNull("fiftyTwoWeekLow"),
            time = metaJson.optLong("regularMarketTime"),
            regularStart = regular?.optLong("start")?.takeIf { it > 0 },
            regularEnd = regular?.optLong("end")?.takeIf { it > 0 },
            timezone = metaJson.optStringOrNull("exchangeTimezoneName"),
        )
        val times = result.optJSONArray("timestamp") ?: JSONArray()
        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")?.optJSONObject(0)
            ?.optJSONArray("close") ?: JSONArray()
        val timestamps = ArrayList<Long>()
        val prices = ArrayList<Double>()
        for (i in 0 until minOf(times.length(), closes.length())) {
            if (closes.isNull(i)) continue
            val price = closes.optDouble(i)
            if (price.isNaN()) continue
            timestamps += times.getLong(i)
            prices += price
        }
        return Chart(
            symbol = meta.symbol,
            timestamps = timestamps,
            prices = prices,
            previousClose = metaJson.optDoubleOrNull("chartPreviousClose") ?: meta.previousClose,
            meta = meta,
        )
    }

    /** Respuesta de `v7/finance/quote`: cotizaciones completas, sin minigráfico. */
    fun parseQuotes(body: String): List<Quote> {
        val result = JSONObject(body).getJSONObject("quoteResponse").optJSONArray("result") ?: return emptyList()
        return (0 until result.length()).mapNotNull { i -> result.optJSONObject(i)?.let(::quoteFrom) }
    }

    /** Respuesta del buscador de Yahoo (`v1/finance/screener/predefined/saved`): las que más se movieron. */
    fun parseScreener(body: String): List<Quote> {
        val result = JSONObject(body).optJSONObject("finance")?.optJSONArray("result")?.optJSONObject(0)
        val quotes = result?.optJSONArray("quotes") ?: return emptyList()
        return (0 until quotes.length()).mapNotNull { i -> quotes.optJSONObject(i)?.let(::quoteFrom) }
    }

    private fun quoteFrom(o: JSONObject): Quote? {
        val symbol = o.optStringOrNull("symbol") ?: return null
        val price = o.optDoubleOrNull("regularMarketPrice") ?: return null
        val previous = o.optDoubleOrNull("regularMarketPreviousClose")
        val change = o.optDoubleOrNull("regularMarketChange") ?: previous?.let { price - it } ?: 0.0
        val changePercent = o.optDoubleOrNull("regularMarketChangePercent")
            ?: previous?.takeIf { it != 0.0 }?.let { change / it * 100 } ?: 0.0
        val state = o.optStringOrNull("marketState")
        val regularTime = o.optLong("regularMarketTime")
        val pre = o.optDoubleOrNull("preMarketPrice")
        val post = o.optDoubleOrNull("postMarketPrice")
        val session = when {
            state == "PRE" && pre != null -> ExtSession.PRE
            state != "REGULAR" && post != null && o.optLong("postMarketTime") >= regularTime -> ExtSession.POST
            else -> null
        }
        val extPrice = when (session) {
            ExtSession.PRE -> pre
            ExtSession.POST -> post
            null -> null
        }
        val extPercent = when (session) {
            ExtSession.PRE -> o.optDoubleOrNull("preMarketChangePercent")
            ExtSession.POST -> o.optDoubleOrNull("postMarketChangePercent")
            null -> null
        } ?: extPrice?.let { (it - price) / price * 100 }
        return Quote(
            symbol = symbol,
            name = o.optStringOrNull("shortName") ?: o.optStringOrNull("longName") ?: symbol,
            price = price,
            change = change,
            changePercent = changePercent,
            previousClose = previous,
            currency = o.optStringOrNull("currency"),
            marketState = state,
            extPrice = extPrice,
            extChangePercent = extPercent,
            extSession = session,
            open = o.optDoubleOrNull("regularMarketOpen"),
            dayHigh = o.optDoubleOrNull("regularMarketDayHigh"),
            dayLow = o.optDoubleOrNull("regularMarketDayLow"),
            volume = o.optDoubleOrNull("regularMarketVolume")?.toLong(),
            yearHigh = o.optDoubleOrNull("fiftyTwoWeekHigh"),
            yearLow = o.optDoubleOrNull("fiftyTwoWeekLow"),
            marketCap = o.optDoubleOrNull("marketCap"),
            exchange = o.optStringOrNull("fullExchangeName") ?: o.optStringOrNull("exchange"),
            quoteType = o.optStringOrNull("quoteType"),
            time = regularTime,
        )
    }

    /**
     * Arma una cotización solo con el gráfico del día (pedido con pre y post), por si Yahoo no
     * responde la cotización completa.
     */
    fun quoteFromChart(chart: Chart): Quote {
        val meta = chart.meta
        val price = meta.price ?: chart.prices.lastOrNull() ?: 0.0
        val previous = meta.previousClose
        val change = previous?.let { price - it } ?: 0.0
        val start = meta.regularStart
        val end = meta.regularEnd
        val lastTime = chart.timestamps.lastOrNull()
        val session = when {
            lastTime == null || start == null || end == null || meta.quoteType == "CRYPTOCURRENCY" -> null
            lastTime >= end -> ExtSession.POST
            lastTime < start -> ExtSession.PRE
            else -> null
        }
        val extPrice = session?.let { chart.prices.last() }
        return Quote(
            symbol = meta.symbol,
            name = meta.name ?: meta.symbol,
            price = price,
            change = change,
            changePercent = previous?.takeIf { it != 0.0 }?.let { change / it * 100 } ?: 0.0,
            previousClose = previous,
            currency = meta.currency,
            marketState = when {
                session == ExtSession.PRE -> "PRE"
                session == ExtSession.POST -> "POST"
                lastTime != null && start != null && end != null && lastTime in start until end -> "REGULAR"
                else -> "CLOSED"
            },
            extPrice = extPrice,
            extChangePercent = extPrice?.takeIf { price != 0.0 }?.let { (it - price) / price * 100 },
            extSession = session,
            spark = regularSession(chart),
            dayHigh = meta.dayHigh,
            dayLow = meta.dayLow,
            volume = meta.volume,
            yearHigh = meta.yearHigh,
            yearLow = meta.yearLow,
            exchange = meta.exchange,
            quoteType = meta.quoteType,
            time = meta.time,
        )
    }

    /** Los precios del horario normal (sin pre ni post) para el minigráfico. */
    fun regularSession(chart: Chart): List<Double> {
        val start = chart.meta.regularStart
        val end = chart.meta.regularEnd
        if (start == null || end == null) return chart.prices
        val inSession = chart.timestamps.indices
            .filter { chart.timestamps[it] in start until end }
            .map { chart.prices[it] }
        return inSession.ifEmpty { chart.prices }
    }

    /** Respuesta de `v1/finance/search`. */
    fun parseSearch(body: String): List<SearchResult> {
        val quotes = JSONObject(body).optJSONArray("quotes") ?: return emptyList()
        return (0 until quotes.length()).mapNotNull { i ->
            val o = quotes.optJSONObject(i) ?: return@mapNotNull null
            val symbol = o.optStringOrNull("symbol") ?: return@mapNotNull null
            // La búsqueda también devuelve noticias y fondos privados sin cotización.
            if (o.has("isYahooFinance") && !o.optBoolean("isYahooFinance")) return@mapNotNull null
            SearchResult(
                symbol = symbol,
                name = o.optStringOrNull("shortname") ?: o.optStringOrNull("longname") ?: symbol,
                exchange = o.optStringOrNull("exchDisp"),
                type = o.optStringOrNull("typeDisp"),
            )
        }
    }

    /** Noticias de `v1/finance/search` (con newsCount > 0). */
    fun parseNews(body: String): List<NewsItem> {
        val news = JSONObject(body).optJSONArray("news") ?: return emptyList()
        return (0 until news.length()).mapNotNull { i ->
            val o = news.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optStringOrNull("title") ?: return@mapNotNull null
            val link = o.optStringOrNull("link") ?: return@mapNotNull null
            // La miniatura más chica que tenga al menos 140 px de ancho.
            val resolutions = o.optJSONObject("thumbnail")?.optJSONArray("resolutions")
            val thumbnail = resolutions?.let { r ->
                (0 until r.length()).mapNotNull { r.optJSONObject(it) }
                    .filter { it.optInt("width") >= 140 }
                    .minByOrNull { it.optInt("width") }
                    ?.optStringOrNull("url")
            }
            NewsItem(
                title = title,
                publisher = o.optStringOrNull("publisher") ?: "",
                link = link,
                time = o.optLong("providerPublishTime"),
                thumbnail = thumbnail,
            )
        }
    }

    /** Símbolos más buscados de `v1/finance/trending/{región}`. */
    fun parseTrending(body: String): List<String> {
        val result = JSONObject(body).optJSONObject("finance")?.optJSONArray("result")?.optJSONObject(0)
        val quotes = result?.optJSONArray("quotes") ?: return emptyList()
        return (0 until quotes.length()).mapNotNull { quotes.optJSONObject(it)?.optStringOrNull("symbol") }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        // Con formatted=true Yahoo manda {"raw": 1.0, "fmt": "1.00"}.
        val value = opt(key)
        val number = if (value is JSONObject) value.optDouble("raw") else optDouble(key)
        return number.takeUnless { it.isNaN() }
    }
}
