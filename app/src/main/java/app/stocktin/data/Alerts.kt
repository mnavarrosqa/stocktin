package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/** Aviso de precio: suena una sola vez, cuando [symbol] llega a [target] (subiendo si [above]). */
data class Alert(
    val id: String,
    val symbol: String,
    val target: Double,
    val above: Boolean,
    val created: Long,
)

object Alerts {

    /** Las alertas que ya se cumplieron con estas cotizaciones. */
    fun reached(alerts: List<Alert>, quotes: Map<String, Quote>): List<Pair<Alert, Quote>> =
        alerts.mapNotNull { alert ->
            val quote = quotes[alert.symbol] ?: return@mapNotNull null
            val hit = if (alert.above) quote.price >= alert.target else quote.price <= alert.target
            if (hit) alert to quote else null
        }

    /**
     * Lee un precio escrito a mano: "150", "150.5", "150,5", "1,234.5" o "1.234,5". Si tiene punto y
     * coma, el último de los dos es el decimal; si tiene solo coma, la coma es el decimal.
     */
    fun parsePrice(text: String): Double? {
        val clean = text.trim().replace(" ", "").removePrefix("$")
        val decimal = when {
            ',' in clean && '.' in clean -> if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) ',' else '.'
            ',' in clean -> ','
            else -> '.'
        }
        val thousands = if (decimal == ',') '.' else ','
        val normalized = clean.replace(thousands.toString(), "").replace(decimal, '.')
        return normalized.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    }

    /** "Sube a 150,00 (+5,2 %)" o "Baja a 120,00 (−3,1 %)", contra el precio de ahora. */
    fun describe(target: Double, price: Double): String {
        val verb = if (target >= price) "Sube a" else "Baja a"
        val percent = if (price != 0.0) (target - price) / price * 100 else 0.0
        return "$verb ${Format.price(target)} (${Format.percent(percent)})"
    }

    fun toJson(alerts: List<Alert>): String = JSONArray().apply {
        alerts.forEach {
            put(
                JSONObject()
                    .put("id", it.id)
                    .put("symbol", it.symbol)
                    .put("target", it.target)
                    .put("above", it.above)
                    .put("created", it.created),
            )
        }
    }.toString()

    fun fromJson(json: String): List<Alert> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Alert(o.getString("id"), o.getString("symbol"), o.getDouble("target"), o.getBoolean("above"), o.optLong("created"))
            }.getOrNull()
        }
    }
}
