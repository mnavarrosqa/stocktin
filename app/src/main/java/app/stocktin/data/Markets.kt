package app.stocktin.data

/** Una sección de la pestaña Mercados. */
data class MarketSection(val title: String, val symbols: List<Pair<String, String>>)

/** Lo que muestra la pestaña Mercados: símbolo y nombre corto. */
val MARKET_SECTIONS = listOf(
    MarketSection(
        "Índices",
        listOf(
            "^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^DJI" to "Dow Jones", "^RUT" to "Russell 2000",
            "^VIX" to "VIX", "^MERV" to "Merval", "^BVSP" to "Bovespa", "^STOXX50E" to "Euro Stoxx 50",
        ),
    ),
    MarketSection("Cripto", listOf("BTC-USD" to "Bitcoin", "ETH-USD" to "Ethereum", "SOL-USD" to "Solana", "USDT-USD" to "Tether")),
    MarketSection("Materias primas", listOf("GC=F" to "Oro", "SI=F" to "Plata", "CL=F" to "Petróleo WTI", "ZS=F" to "Soja")),
    MarketSection(
        "Monedas y tasas",
        listOf("ARS=X" to "Dólar / Peso", "EURUSD=X" to "Euro / Dólar", "BRL=X" to "Dólar / Real", "^TNX" to "Bono 10 años EE.UU."),
    ),
)

val MARKET_SYMBOLS: List<String> = MARKET_SECTIONS.flatMap { section -> section.symbols.map { it.first } }
