package app.stocktin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Datos de Yahoo Finance (la misma fuente que su app). No hace falta clave: la cotización completa
 * pide una cookie y un "crumb" que se consiguen solos; si fallan, se arma con el gráfico del día.
 */
object YahooApi {

    private const val HOST = "https://query1.finance.yahoo.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    @Volatile
    private var crumb: String? = null

    /** Solo para las capturas de pantalla: respuestas armadas en vez de ir a Yahoo. */
    @Volatile
    var fakeResponses: ((url: String) -> String)? = null

    init {
        CookieHandler.setDefault(cookies)
    }

    /** Cotizaciones con minigráfico del día, en el mismo orden que [symbols]. */
    suspend fun quotes(symbols: List<String>): List<Quote> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyList()
        coroutineScope {
            val full = async { runCatching { fullQuotes(symbols) }.getOrDefault(emptyMap()) }
            val gate = Semaphore(6)
            val charts = symbols.map { symbol ->
                async { gate.withPermit { runCatching { chart(symbol, "1d", "5m", prePost = true) }.getOrNull() } }
            }.awaitAll()
            val bySymbol = full.await()
            symbols.mapIndexedNotNull { i, symbol ->
                val chart = charts[i]
                val quote = bySymbol[symbol]
                when {
                    quote != null -> quote.copy(spark = chart?.let(YahooParser::regularSession).orEmpty())
                    chart != null -> YahooParser.quoteFromChart(chart)
                    else -> null
                }
            }.also { if (it.isEmpty()) throw IOException("No se pudo conectar con Yahoo Finance") }
        }
    }

    suspend fun chart(symbol: String, range: ChartRange): Chart = withContext(Dispatchers.IO) {
        chart(symbol, range.range, range.interval, prePost = false)
    }

    /** El gráfico del día con pre-apertura y post-cierre. */
    suspend fun chartExtended(symbol: String): Chart = withContext(Dispatchers.IO) {
        chart(symbol, ChartRange.DAY.range, ChartRange.DAY.interval, prePost = true)
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        YahooParser.parseSearch(get("$HOST/v1/finance/search?q=$q&quotesCount=12&newsCount=0&listsCount=0"))
    }

    suspend fun news(symbol: String): List<NewsItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(symbol, "UTF-8")
        YahooParser.parseNews(get("$HOST/v1/finance/search?q=$q&quotesCount=0&newsCount=8&listsCount=0"))
    }

    /** Las acciones de EE.UU. que más suben, bajan o se operan hoy. */
    suspend fun movers(kind: Movers, count: Int = 8): List<Quote> = withContext(Dispatchers.IO) {
        val url = "$HOST/v1/finance/screener/predefined/saved?scrIds=${kind.screenerId}&count=$count&formatted=false"
        val body = try {
            get(url)
        } catch (e: HttpException) {
            // Algunas veces pide el crumb, como la cotización completa.
            get(url + "&crumb=" + URLEncoder.encode(crumb(), "UTF-8"))
        }
        YahooParser.parseScreener(body)
    }

    /** Lo más buscado hoy en Yahoo Finance (EE.UU.). */
    suspend fun trending(): List<String> = withContext(Dispatchers.IO) {
        YahooParser.parseTrending(get("$HOST/v1/finance/trending/US?count=12"))
    }

    private fun chart(symbol: String, range: String, interval: String, prePost: Boolean): Chart {
        val s = URLEncoder.encode(symbol, "UTF-8")
        return YahooParser.parseChart(
            get("$HOST/v8/finance/chart/$s?range=$range&interval=$interval&includePrePost=$prePost"),
        )
    }

    private fun fullQuotes(symbols: List<String>): Map<String, Quote> {
        val list = URLEncoder.encode(symbols.joinToString(","), "UTF-8")
        val url = "$HOST/v7/finance/quote?symbols=$list&formatted=false&crumb="
        val body = try {
            get(url + URLEncoder.encode(crumb(), "UTF-8"))
        } catch (e: HttpException) {
            // El crumb vence: se pide otro una vez.
            if (e.code != 401 && e.code != 403) throw e
            crumb = null
            get(url + URLEncoder.encode(crumb(), "UTF-8"))
        }
        return YahooParser.parseQuotes(body).associateBy { it.symbol }
    }

    private fun crumb(): String {
        crumb?.let { return it }
        // fc.yahoo.com responde 404 pero deja la cookie que pide el crumb.
        runCatching { get("https://fc.yahoo.com") }
        val fresh = get("$HOST/v1/test/getcrumb").trim()
        if (fresh.isEmpty() || fresh.contains('<') || fresh.contains('{')) throw IOException("Sin crumb")
        crumb = fresh
        return fresh
    }

    /**
     * La agenda económica (`calendar.json`) tal como está en GitHub, para poder sumar fechas sin
     * sacar una versión nueva de la app.
     */
    suspend fun calendar(): String = withContext(Dispatchers.IO) { get(CALENDAR_URL) }

    private const val CALENDAR_URL =
        "https://raw.githubusercontent.com/mnavarrosqa/stocktin/main/app/src/main/assets/calendar.json"

    class HttpException(val code: Int) : IOException("Yahoo respondió $code")

    private fun get(url: String): String {
        fakeResponses?.let { return it(url) }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
