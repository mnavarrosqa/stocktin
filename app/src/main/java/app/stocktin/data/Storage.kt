package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/** Pasa listas y cotizaciones a JSON y de vuelta (para guardarlas en archivos de la app). */
object Storage {

    val DEFAULT_LISTS = listOf(
        Watchlist(
            id = "main",
            name = "Mi lista",
            symbols = listOf("SPY", "QQQ", "^GSPC", "^NDX", "NVDA", "AAPL", "BTC-USD", "^VIX"),
        ),
    )

    fun listsToJson(lists: List<Watchlist>): String = JSONArray().apply {
        lists.forEach { list ->
            put(
                JSONObject()
                    .put("id", list.id)
                    .put("name", list.name)
                    .put("symbols", JSONArray(list.symbols)),
            )
        }
    }.toString()

    fun listsFromJson(json: String): List<Watchlist> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val symbols = o.getJSONArray("symbols")
            Watchlist(
                id = o.getString("id"),
                name = o.getString("name"),
                symbols = (0 until symbols.length()).map { symbols.getString(it) },
            )
        }
    }

    fun quotesToJson(quotes: Collection<Quote>): String = JSONArray().apply {
        quotes.forEach { put(quoteToJson(it)) }
    }.toString()

    fun quotesFromJson(json: String): List<Quote> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { runCatching { quoteFromJson(array.getJSONObject(it)) }.getOrNull() }
    }

    private fun quoteToJson(q: Quote) = JSONObject().apply {
        put("symbol", q.symbol)
        put("name", q.name)
        put("price", q.price)
        put("change", q.change)
        put("changePercent", q.changePercent)
        putOpt("previousClose", q.previousClose)
        putOpt("currency", q.currency)
        putOpt("marketState", q.marketState)
        putOpt("extPrice", q.extPrice)
        putOpt("extChangePercent", q.extChangePercent)
        putOpt("extSession", q.extSession?.name)
        put("spark", JSONArray().apply { q.spark.forEach { put(it) } })
        putOpt("open", q.open)
        putOpt("dayHigh", q.dayHigh)
        putOpt("dayLow", q.dayLow)
        putOpt("volume", q.volume)
        putOpt("yearHigh", q.yearHigh)
        putOpt("yearLow", q.yearLow)
        putOpt("marketCap", q.marketCap)
        putOpt("exchange", q.exchange)
        putOpt("quoteType", q.quoteType)
        put("time", q.time)
    }

    private fun quoteFromJson(o: JSONObject): Quote {
        val spark = o.optJSONArray("spark") ?: JSONArray()
        return Quote(
            symbol = o.getString("symbol"),
            name = o.getString("name"),
            price = o.getDouble("price"),
            change = o.getDouble("change"),
            changePercent = o.getDouble("changePercent"),
            previousClose = o.double("previousClose"),
            currency = o.string("currency"),
            marketState = o.string("marketState"),
            extPrice = o.double("extPrice"),
            extChangePercent = o.double("extChangePercent"),
            extSession = o.string("extSession")?.let { name -> ExtSession.entries.firstOrNull { it.name == name } },
            spark = (0 until spark.length()).map { spark.getDouble(it) },
            open = o.double("open"),
            dayHigh = o.double("dayHigh"),
            dayLow = o.double("dayLow"),
            volume = if (o.has("volume")) o.getLong("volume") else null,
            yearHigh = o.double("yearHigh"),
            yearLow = o.double("yearLow"),
            marketCap = o.double("marketCap"),
            exchange = o.string("exchange"),
            quoteType = o.string("quoteType"),
            time = o.optLong("time"),
        )
    }

    private fun JSONObject.double(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.string(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
