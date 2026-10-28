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
