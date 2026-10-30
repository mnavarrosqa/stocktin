package app.stocktin.data

/** Cómo se llama en Buenos Aires (en pesos) un símbolo de EE.UU.: su CEDEAR o, si es un ADR argentino, la acción local. */
data class LocalListing(
    /** El símbolo en Yahoo, por ejemplo AAPL.BA. */
    val symbol: String,
    /** "CEDEARs" o "Acciones en pesos". */
    val label: String,
)

object Cedears {

    /** ADRs argentinos: en Buenos Aires se opera la acción local, que a veces tiene otro nombre. */
    private val ARGENTINE_ADRS = mapOf(
        "GGAL" to "GGAL.BA", "YPF" to "YPFD.BA", "PAM" to "PAMP.BA", "BMA" to "BMA.BA", "TGS" to "TGSU2.BA",
        "CEPU" to "CEPU.BA", "BBAR" to "BBAR.BA", "SUPV" to "SUPV.BA", "LOMA" to "LOMA.BA", "EDN" to "EDN.BA",
        "CRESY" to "CRES.BA", "TEO" to "TECO2.BA", "IRS" to "IRSA.BA",
    )

    /** CEDEARs cuyo símbolo no es el de EE.UU. con ".BA". */
    private val RENAMED = mapOf("BRK-B" to "BRKB.BA")

    /**
     * Lo que se puede tener en pesos de [symbol]: el CEDEAR de una acción o un ETF de EE.UU., o la
     * acción local de un ADR argentino. Null para índices, monedas, cripto, futuros y lo que ya
     * cotiza en otra bolsa.
     */
    fun localFor(symbol: String, quote: Quote?): LocalListing? {
        ARGENTINE_ADRS[symbol]?.let { return LocalListing(it, "Acciones en pesos") }
        RENAMED[symbol]?.let { return LocalListing(it, "CEDEARs") }
        if (symbol.any { it in ".-=^" }) return null
        val type = quote?.quoteType
        if (type != null && type != "EQUITY" && type != "ETF") return null
        if (quote?.currency != null && quote.currency != "USD") return null
        return LocalListing("$symbol.BA", "CEDEARs")
    }
}
