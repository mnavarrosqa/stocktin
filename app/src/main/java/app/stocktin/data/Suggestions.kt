package app.stocktin.data

/** Una lista armada para agregar con un toque. */
data class SuggestedList(
    val id: String,
    val name: String,
    val description: String,
    val symbols: List<String>,
)

/** Ideas de listas para armar; vienen con la app. */
val SUGGESTED_LISTS = listOf(
    SuggestedList(
        "mag7", "Las 7 magníficas",
        "Las tecnológicas más grandes de EE.UU., las que más pesan en el S&P 500",
        listOf("AAPL", "MSFT", "NVDA", "GOOGL", "AMZN", "META", "TSLA"),
    ),
    SuggestedList(
        "adrs", "ADRs argentinos",
        "Empresas argentinas que cotizan en Wall Street, en dólares",
        listOf("GGAL", "YPF", "PAM", "BMA", "VIST", "TGS", "CEPU", "BBAR", "MELI", "GLOB"),
    ),
    SuggestedList(
        "merval", "Panel líder",
        "Las acciones más operadas de la Bolsa de Buenos Aires, en pesos",
        listOf("GGAL.BA", "YPFD.BA", "PAMP.BA", "BMA.BA", "TXAR.BA", "ALUA.BA", "CEPU.BA", "TGSU2.BA", "BBAR.BA", "LOMA.BA"),
    ),
    SuggestedList(
        "ai", "Inteligencia artificial",
        "Chips, nube y software detrás del boom de la IA",
        listOf("NVDA", "AMD", "AVGO", "TSM", "MSFT", "GOOGL", "PLTR", "ARM"),
    ),
    SuggestedList(
        "crypto", "Cripto",
        "Las criptomonedas más grandes, contra el dólar; operan las 24 horas",
        listOf("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD", "BNB-USD", "DOGE-USD"),
    ),
    SuggestedList(
        "etfs", "ETFs para el mundo",
        "Un fondo por mercado: EE.UU., tecnología, chicas, emergentes, Brasil y Argentina",
        listOf("SPY", "QQQ", "DIA", "IWM", "EEM", "EWZ", "ARGT", "VT"),
    ),
    SuggestedList(
        "dividends", "Dividendos",
        "Empresas grandes y estables que reparten dividendos hace años",
        listOf("KO", "PEP", "JNJ", "PG", "XOM", "JPM", "VZ", "O"),
    ),
    SuggestedList(
        "commodities", "Materias primas",
        "Oro, plata, petróleo, gas, cobre, soja y maíz (futuros)",
        listOf("GC=F", "SI=F", "CL=F", "NG=F", "HG=F", "ZS=F", "ZC=F"),
    ),
    SuggestedList(
        "fx", "Dólar y tasas",
        "El peso, el real, el euro y el yen contra el dólar, el índice dólar y el bono a 10 años",
        listOf("ARS=X", "BRL=X", "EURUSD=X", "JPY=X", "DX-Y.NYB", "^TNX"),
    ),
)

object Suggestions {

    /** Las que todavía no armaste (ninguna lista se llama igual). */
    fun available(lists: List<Watchlist>): List<SuggestedList> {
        val names = lists.map { it.name.trim().lowercase() }.toSet()
        return SUGGESTED_LISTS.filter { it.name.lowercase() !in names }
    }

    /** Cuánto se movió hoy la lista en promedio, con los símbolos que tienen cotización. */
    fun averageChange(list: SuggestedList, quotes: Map<String, Quote>): Double? =
        list.symbols.mapNotNull { quotes[it]?.changePercent }.takeIf { it.isNotEmpty() }?.average()
}
