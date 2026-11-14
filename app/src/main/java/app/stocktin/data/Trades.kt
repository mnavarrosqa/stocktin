package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/** Una compra o una venta: cuántas, a qué precio y qué día (yyyy-MM-dd). */
data class Trade(
    val id: String,
    val symbol: String,
    val date: String,
    val shares: Double,
    val price: Double,
    val buy: Boolean,
)

/** Lo que queda después de todas las operaciones de un símbolo. */
data class TradeSummary(
    /** Lo que tenés hoy (null si vendiste todo). */
    val holding: Holding?,
    /** Lo que ganaste (o perdiste) con lo que ya vendiste. */
    val realized: Double,
)

object Trades {

    /**
     * Repasa las operaciones por fecha con el método del precio promedio: cada compra mueve el
     * promedio; cada venta no lo toca y suma (o resta) a la ganancia realizada. Si se vende más de
     * lo que se tenía, se vende lo que hay.
     */
    fun summarize(symbol: String, trades: List<Trade>): TradeSummary {
        var shares = 0.0
        var cost = 0.0
        var realized = 0.0
        trades.filter { it.symbol == symbol }.sortedWith(compareBy({ it.date }, { !it.buy })).forEach { t ->
            if (t.buy) {
                cost = (shares * cost + t.shares * t.price) / (shares + t.shares)
                shares += t.shares
            } else {
                val sold = minOf(t.shares, shares)
                realized += sold * (t.price - cost)
                shares -= sold
            }
        }
        val holding = if (shares > 1e-9) Holding(symbol, shares, cost) else null
        return TradeSummary(holding, realized)
    }

    fun toJson(trades: List<Trade>): String = JSONArray().apply {
        trades.forEach {
            put(
                JSONObject().put("id", it.id).put("symbol", it.symbol).put("date", it.date)
                    .put("shares", it.shares).put("price", it.price).put("buy", it.buy),
            )
        }
    }.toString()

    fun fromJson(json: String): List<Trade> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Trade(o.getString("id"), o.getString("symbol"), o.getString("date"), o.getDouble("shares"), o.getDouble("price"), o.getBoolean("buy"))
            }.getOrNull()
        }
    }
}
