package app.stocktin.data

/** Para comparar un símbolo contra su índice de referencia en el gráfico del detalle. */
object ChartCompare {

    /** El índice contra el que se compara [symbol] (símbolo y nombre), o null si no tiene sentido. */
    fun benchmarkFor(symbol: String): Pair<String, String>? = when {
        symbol == "^GSPC" || symbol == "^MERV" -> null
        symbol.endsWith(".BA") -> "^MERV" to "Merval"
        else -> "^GSPC" to "S&P 500"
    }

    /**
     * La serie [other] llevada a los horarios y a la escala de [base]: arranca en [baseStart] y se
     * mueve en el mismo porcentaje que [other] desde [otherStart] (o desde su primer valor). Así las
     * dos líneas se dibujan con el mismo eje y la distancia entre ellas es la diferencia de rendimiento.
     */
    fun overlay(base: Chart, baseStart: Double, other: Chart, otherStart: Double?): List<Double> {
        if (base.prices.isEmpty() || other.prices.isEmpty()) return emptyList()
        var j = 0
        val aligned = base.timestamps.map { t ->
            while (j + 1 < other.timestamps.size && other.timestamps[j + 1] <= t) j++
            other.prices[j]
        }
        val start = otherStart?.takeIf { it > 0 } ?: aligned.first()
        return aligned.map { baseStart * it / start }
    }

    /** Cuánto cambió una serie superpuesta, en porcentaje, desde [baseStart]. */
    fun percent(series: List<Double>, baseStart: Double, index: Int? = null): Double? {
        val value = (index?.let { series.getOrNull(it) } ?: series.lastOrNull()) ?: return null
        return if (baseStart != 0.0) (value / baseStart - 1) * 100 else null
    }
}
