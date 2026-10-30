package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Un aviso. De precio: suena una sola vez, cuando [symbol] llega a [target] (subiendo si [above]).
 * De movimiento del día: [target] es un porcentaje y suena (una vez por día) cuando el símbolo sube o
 * baja más que eso en el día.
 */
data class Alert(
    val id: String,
    val symbol: String,
    val target: Double,
    val above: Boolean,
    val created: Long,
    val kind: AlertKind = AlertKind.PRICE,
    /** Día (yyyy-MM-dd) en que sonó por última vez una alerta de movimiento. */
    val lastFired: String? = null,
)

enum class AlertKind { PRICE, DAY_MOVE }

object Alerts {

    /** Las alertas que se cumplieron con estas cotizaciones ([today] es yyyy-MM-dd). */
    fun reached(
        alerts: List<Alert>,
        quotes: Map<String, Quote>,
        today: String,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<Pair<Alert, Quote>> =
        alerts.mapNotNull { alert ->
            val quote = quotes[alert.symbol] ?: return@mapNotNull null
            val hit = when (alert.kind) {
                AlertKind.PRICE -> if (alert.above) quote.price >= alert.target else quote.price <= alert.target
                // Solo con cotizaciones de hoy: con la bolsa cerrada sigue figurando el cambio de ayer.
                AlertKind.DAY_MOVE -> alert.lastFired != today &&
                    (quote.time == 0L || java.time.Instant.ofEpochSecond(quote.time).atZone(zone).toLocalDate().toString() == today) &&
                    kotlin.math.abs(quote.changePercent) >= alert.target
            }
            if (hit) alert to quote else null
        }

    /**
     * Lee un precio escrito a mano: "150", "150.5", "150,5", "1,234.5" o "1.234,5". Si tiene punto y
     * coma, el último de los dos es el decimal; si tiene solo coma, la coma es el decimal. Con
     * [pesos], como se escribe en Argentina: un punto seguido de tres cifras es de miles ("19.990" son
     * diecinueve mil novecientos noventa).
     */
    fun parsePrice(text: String, pesos: Boolean = false): Double? {
        val clean = text.trim().replace(" ", "").removePrefix("$")
        val decimal = when {
            ',' in clean && '.' in clean -> if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) ',' else '.'
            ',' in clean -> ','
            pesos && '.' in clean && clean.split('.').drop(1).all { it.length == 3 } -> ','
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
                    .put("created", it.created)
                    .put("kind", it.kind.name)
                    .putOpt("lastFired", it.lastFired),
            )
        }
    }.toString()

    fun fromJson(json: String): List<Alert> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Alert(
                    id = o.getString("id"),
                    symbol = o.getString("symbol"),
                    target = o.getDouble("target"),
                    above = o.getBoolean("above"),
                    created = o.optLong("created"),
                    kind = AlertKind.entries.firstOrNull { it.name == o.optString("kind") } ?: AlertKind.PRICE,
                    lastFired = if (o.has("lastFired") && !o.isNull("lastFired")) o.getString("lastFired") else null,
                )
            }.getOrNull()
        }
    }
}
