package app.stocktin.data

/** El dólar en Argentina: el oficial y el contado con liqui (CCL) implícito en las acciones. */
data class DollarRates(
    /** Dólar oficial (mayorista, el que informa Yahoo como ARS=X). */
    val official: Double?,
    /** CCL: lo que vale un dólar comprando una acción acá en pesos y vendiéndola afuera en dólares. */
    val ccl: Double?,
    /** Cambio del CCL hoy, en %, si se puede calcular. */
    val cclChangePercent: Double?,
) {
    /** Cuánto más caro está el CCL que el oficial, en %. */
    val gap: Double? get() = if (official != null && ccl != null && official > 0) (ccl / official - 1) * 100 else null
}

object Dollar {

    /** Acción local, su ADR en Nueva York y cuántas acciones locales hay en cada ADR. */
    private val PAIRS = listOf(
        Triple("GGAL.BA", "GGAL", 10.0),
        Triple("YPFD.BA", "YPF", 1.0),
        Triple("PAMP.BA", "PAM", 25.0),
        Triple("BMA.BA", "BMA", 10.0),
    )

    const val OFFICIAL = "ARS=X"

    /** Todo lo que hace falta cotizar para calcularlo. */
    val SYMBOLS: List<String> = listOf(OFFICIAL) + PAIRS.flatMap { listOf(it.first, it.second) }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * El CCL es la mediana de lo que da cada par (así una cotización rara no lo mueve); el de ayer
     * se calcula igual con los cierres anteriores, para saber cuánto cambió hoy.
     */
    fun rates(quotes: Map<String, Quote>): DollarRates {
        fun implied(price: (Quote) -> Double?) = median(
            PAIRS.mapNotNull { (local, adr, ratio) ->
                val l = quotes[local]?.let(price) ?: return@mapNotNull null
                val a = quotes[adr]?.let(price)?.takeIf { it > 0 } ?: return@mapNotNull null
                l * ratio / a
            },
        )
        val now = implied { it.price }
        val before = implied { it.previousClose }
        return DollarRates(
            official = quotes[OFFICIAL]?.price,
            ccl = now,
            cclChangePercent = if (now != null && before != null && before > 0) (now / before - 1) * 100 else null,
        )
    }
}

/** Lo que vale la cartera cada día (por moneda), para el gráfico de su evolución. */
object PortfolioHistory {

    /** Guarda el valor de [day] (yyyy-MM-dd), pisando el de ese mismo día; se quedan los últimos 5 años. */
    fun record(history: Map<String, Map<String, Double>>, day: String, values: Map<String, Double>): Map<String, Map<String, Double>> {
        if (values.isEmpty()) return history
        val cutoff = java.time.LocalDate.parse(day).minusYears(5).toString()
        return (history + (day to values)).filterKeys { it >= cutoff }.toSortedMap()
    }

    /** Los valores de [currency] desde [from] (yyyy-MM-dd), ordenados por día. */
    fun series(history: Map<String, Map<String, Double>>, currency: String, from: String = ""): List<Pair<String, Double>> =
        history.toSortedMap().filterKeys { it >= from }.mapNotNull { (day, values) -> values[currency]?.let { day to it } }

    fun toJson(history: Map<String, Map<String, Double>>): String = org.json.JSONObject().apply {
        history.forEach { (day, values) -> put(day, org.json.JSONObject(values)) }
    }.toString()

    fun fromJson(json: String): Map<String, Map<String, Double>> {
        val o = org.json.JSONObject(json)
        return o.keys().asSequence().associateWith { day ->
            val v = o.getJSONObject(day)
            v.keys().asSequence().associateWith { v.getDouble(it) }
        }.toSortedMap()
    }
}
