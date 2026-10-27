package app.stocktin.data

/** Cómo se muestra una sección de Mercados. */
enum class SectionStyle {
    /** Tarjetas con precio, cambio y gráfico del día. */
    CARDS,
    /** Baldosas de color según cuánto se movió cada una (mapa de calor). */
    HEATMAP,
}

/** Una sección de la pestaña Mercados: símbolo y nombre corto de cada uno. */
data class MarketSection(
    val title: String,
    val symbols: List<Pair<String, String>>,
    val style: SectionStyle = SectionStyle.CARDS,
)

val MARKET_SECTIONS = listOf(
    MarketSection(
        "Índices",
        listOf(
            "^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^DJI" to "Dow Jones", "^RUT" to "Russell 2000",
            "^VIX" to "VIX", "^BVSP" to "Bovespa", "^STOXX50E" to "Euro Stoxx 50", "^N225" to "Nikkei 225",
        ),
    ),
    MarketSection(
        "Argentina",
        listOf(
            "^MERV" to "Merval", "GGAL" to "Galicia", "YPF" to "YPF", "BMA" to "Macro",
            "PAM" to "Pampa", "MELI" to "Mercado Libre", "VIST" to "Vista", "ARS=X" to "Dólar / Peso",
        ),
    ),
    MarketSection(
        "Sectores de EE.UU.",
        listOf(
            "XLK" to "Tecnología", "XLF" to "Finanzas", "XLV" to "Salud", "XLY" to "Consumo",
            "XLC" to "Comunicación", "XLI" to "Industria", "XLP" to "Consumo básico", "XLE" to "Energía",
            "XLU" to "Servicios", "XLRE" to "Inmobiliario", "XLB" to "Materiales",
        ),
        SectionStyle.HEATMAP,
    ),
    MarketSection("Cripto", listOf("BTC-USD" to "Bitcoin", "ETH-USD" to "Ethereum", "SOL-USD" to "Solana", "XRP-USD" to "XRP")),
    MarketSection("Materias primas", listOf("GC=F" to "Oro", "SI=F" to "Plata", "CL=F" to "Petróleo WTI", "ZS=F" to "Soja")),
    MarketSection(
        "Monedas y tasas",
        listOf("EURUSD=X" to "Euro / Dólar", "BRL=X" to "Dólar / Real", "^TNX" to "Bono 10 años", "^IRX" to "Letra 3 meses"),
    ),
)

/** La tira de arriba de Listas: cómo viene el día de un vistazo. */
val STRIP_SYMBOLS = listOf("^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^DJI" to "Dow Jones", "BTC-USD" to "Bitcoin")

val MARKET_SYMBOLS: List<String> = MARKET_SECTIONS.flatMap { section -> section.symbols.map { it.first } }

/** Listas de Yahoo con lo que más se movió en el día en EE.UU. */
enum class Movers(val label: String, val screenerId: String) {
    GAINERS("Suben", "day_gainers"),
    LOSERS("Bajan", "day_losers"),
    ACTIVE("Más operadas", "most_actives"),
}
