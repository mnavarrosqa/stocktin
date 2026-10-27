package app.stocktin.data

/** Cotización de un símbolo tal como la muestran la app y los widgets. */
data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val previousClose: Double?,
    val currency: String?,
    /** REGULAR, PRE, POST, CLOSED… (el estado del mercado según Yahoo). */
    val marketState: String?,
    /** Precio fuera de horario (pre-apertura o post-cierre), si corresponde mostrarlo. */
    val extPrice: Double? = null,
    val extChangePercent: Double? = null,
    val extSession: ExtSession? = null,
    /** Precios del día para el minigráfico. */
    val spark: List<Double> = emptyList(),
    val open: Double? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val volume: Long? = null,
    val yearHigh: Double? = null,
    val yearLow: Double? = null,
    val marketCap: Double? = null,
    val exchange: String? = null,
    val quoteType: String? = null,
    /** Hora de la última cotización, en segundos desde 1970. */
    val time: Long = 0,
) {
    val isUp: Boolean get() = change >= 0
    val isOpen: Boolean get() = marketState == "REGULAR" || quoteType == "CRYPTOCURRENCY"
}

/** Sesión fuera de horario. */
enum class ExtSession(val label: String) {
    PRE("Pre"),
    POST("Post"),
}

/** Una serie de precios para graficar. */
data class Chart(
    val symbol: String,
    /** Segundos desde 1970. */
    val timestamps: List<Long>,
    val prices: List<Double>,
    val previousClose: Double?,
    val meta: ChartMeta,
)

/** Lo que trae el endpoint de gráficos además de los precios. */
data class ChartMeta(
    val symbol: String,
    val name: String?,
    val price: Double?,
    val previousClose: Double?,
    val currency: String?,
    val exchange: String?,
    val quoteType: String?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val volume: Long?,
    val yearHigh: Double?,
    val yearLow: Double?,
    val time: Long,
    val regularStart: Long?,
    val regularEnd: Long?,
    val timezone: String?,
)

/** Un resultado de la búsqueda de símbolos. */
data class SearchResult(
    val symbol: String,
    val name: String,
    val exchange: String?,
    val type: String?,
)

/** Una noticia sobre un símbolo. */
data class NewsItem(
    val title: String,
    val publisher: String,
    val link: String,
    /** Segundos desde 1970. */
    val time: Long,
    val thumbnail: String?,
)

/** Una lista de seguimiento. */
data class Watchlist(
    val id: String,
    val name: String,
    val symbols: List<String>,
)

/** Rango del gráfico grande. */
enum class ChartRange(val label: String, val range: String, val interval: String) {
    DAY("1D", "1d", "5m"),
    WEEK("5D", "5d", "30m"),
    MONTH("1M", "1mo", "1d"),
    SIX_MONTHS("6M", "6mo", "1d"),
    YTD("YTD", "ytd", "1d"),
    YEAR("1A", "1y", "1d"),
    FIVE_YEARS("5A", "5y", "1wk"),
    MAX("Máx", "max", "1mo"),
}

/** Orden de los símbolos en la lista de la app (los widgets siguen el orden propio de la lista). */
enum class SortMode(val label: String) {
    CUSTOM("Personalizado"),
    GAINERS("Mayor suba"),
    LOSERS("Mayor baja"),
    NAME("Símbolo A-Z");

    fun apply(symbols: List<String>, quotes: Map<String, Quote>): List<String> = when (this) {
        CUSTOM -> symbols
        GAINERS -> symbols.sortedByDescending { quotes[it]?.changePercent ?: Double.NEGATIVE_INFINITY }
        LOSERS -> symbols.sortedBy { quotes[it]?.changePercent ?: Double.POSITIVE_INFINITY }
        NAME -> symbols.sortedBy { it.removePrefix("^") }
    }

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.name == key } ?: CUSTOM
    }
}
