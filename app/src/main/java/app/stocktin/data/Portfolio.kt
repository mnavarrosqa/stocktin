package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/** Lo que tenés de un símbolo: cuántas unidades y a qué precio promedio las compraste. */
data class Holding(
    val symbol: String,
    val shares: Double,
    /** Precio promedio de compra por unidad. */
    val cost: Double,
)

/** Cuánto vale lo que tenés de un símbolo (o de toda la cartera) y cuánto ganaste. */
data class Position(
    val value: Double,
    val dayChange: Double,
    val costBasis: Double,
) {
    val gain: Double get() = value - costBasis
    val gainPercent: Double get() = if (costBasis > 0) gain / costBasis * 100 else 0.0
    /** El cambio de hoy como porcentaje de lo que valía ayer. */
    val dayPercent: Double get() = (value - dayChange).takeIf { it > 0 }?.let { dayChange / it * 100 } ?: 0.0

    operator fun plus(other: Position) = Position(value + other.value, dayChange + other.dayChange, costBasis + other.costBasis)
}

/** Totales de la cartera en una moneda. */
data class PortfolioTotal(val currency: String?, val position: Position, val count: Int)

object Portfolio {

    fun position(holding: Holding, quote: Quote): Position =
        Position(
            value = holding.shares * quote.price,
            dayChange = holding.shares * quote.change,
            costBasis = holding.shares * holding.cost,
        )

    /**
     * Los totales, uno por moneda (no se mezclan dólares con pesos), el de más valor primero. Los
     * símbolos sin cotización todavía no se cuentan.
     */
    fun totals(holdings: Collection<Holding>, quotes: Map<String, Quote>): List<PortfolioTotal> =
        holdings.mapNotNull { h -> quotes[h.symbol]?.let { q -> q.currency to position(h, q) } }
            .groupBy({ it.first }, { it.second })
            .map { (currency, positions) -> PortfolioTotal(currency, positions.reduce(Position::plus), positions.size) }
            .sortedByDescending { it.position.value }

    /**
     * Qué parte de la cartera (en [currency]) es cada símbolo, de mayor a menor. Con más de
     * [max] símbolos, los más chicos se juntan en "Otros".
     */
    fun allocation(holdings: Collection<Holding>, quotes: Map<String, Quote>, currency: String?, max: Int = 5): List<Pair<String, Double>> {
        val values = holdings.mapNotNull { h ->
            val q = quotes[h.symbol]?.takeIf { it.currency == currency } ?: return@mapNotNull null
            h.symbol to h.shares * q.price
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val total = values.sumOf { it.second }
        if (total <= 0) return emptyList()
        val shares = values.map { it.first to it.second / total }
        if (shares.size <= max) return shares
        return shares.take(max - 1) + ("Otros" to shares.drop(max - 1).sumOf { it.second })
    }

    /** Qué parte de su cartera (en su moneda) es [symbol], de 0 a 1. */
    fun weight(symbol: String, holdings: Collection<Holding>, quotes: Map<String, Quote>): Double? {
        val currency = quotes[symbol]?.currency
        val all = allocation(holdings, quotes, currency, max = Int.MAX_VALUE)
        return all.firstOrNull { it.first == symbol }?.second?.takeIf { all.size > 1 }
    }

    /**
     * Si un precio promedio en pesos quedó mil veces más chico que el de ahora, casi seguro se
     * escribió "19.990" y se leyó 19,99 (antes de la v0.1.71): devuelve la posición corregida.
     */
    fun fixMistypedPesos(holding: Holding, quote: Quote?): Holding? {
        if (quote?.currency != "ARS" || holding.cost <= 0) return null
        val ratio = quote.price / holding.cost
        return if (ratio in 300.0..3000.0) holding.copy(cost = holding.cost * 1000) else null
    }

    fun toJson(holdings: Collection<Holding>): String = JSONArray().apply {
        holdings.forEach { put(JSONObject().put("symbol", it.symbol).put("shares", it.shares).put("cost", it.cost)) }
    }.toString()

    fun fromJson(json: String): List<Holding> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Holding(o.getString("symbol"), o.getDouble("shares"), o.getDouble("cost"))
            }.getOrNull()
        }
    }
}
