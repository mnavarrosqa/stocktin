package app.stocktin.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

/** Un balance trimestral: lo que ganó por acción contra lo que esperaban los analistas. */
data class EarningsResult(
    /** "T3 2026". */
    val quarter: String,
    val actual: Double,
    val estimate: Double?,
    /** Sorpresa en %, positiva si ganó más de lo esperado. */
    val surprise: Double?,
)

/** Lo que cuenta Yahoo de la empresa: a qué se dedica y cómo le fue en los últimos balances. */
data class CompanyInfo(
    val sector: String?,
    val industry: String?,
    val summary: String?,
    val website: String?,
    val employees: Long?,
    /** Del más viejo al más nuevo. */
    val earnings: List<EarningsResult>,
)

object Company {

    /** Yahoo manda los números como {"raw": 1.2, "fmt": "1.20"} o como número suelto. */
    private fun JSONObject.number(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        optJSONObject(key)?.let { o -> return if (o.has("raw")) o.optDouble("raw").takeIf { !it.isNaN() } else null }
        return optDouble(key).takeIf { !it.isNaN() }
    }

    /** Respuesta de `v10/finance/quoteSummary` con los módulos assetProfile y earningsHistory. */
    fun parse(body: String): CompanyInfo? {
        val result = JSONObject(body).optJSONObject("quoteSummary")?.optJSONArray("result")?.optJSONObject(0) ?: return null
        val profile = result.optJSONObject("assetProfile")
        val history = result.optJSONObject("earningsHistory")?.optJSONArray("history")
        val earnings = (0 until (history?.length() ?: 0)).mapNotNull { i ->
            val h = history!!.optJSONObject(i) ?: return@mapNotNull null
            val actual = h.number("epsActual") ?: return@mapNotNull null
            val estimate = h.number("epsEstimate")
            // El trimestre viene como fecha de cierre (segundos): marzo → T1, junio → T2…
            val quarter = h.number("quarter")?.let { seconds ->
                val d = Instant.ofEpochSecond(seconds.toLong()).atZone(ZoneOffset.UTC).toLocalDate()
                "T${(d.monthValue - 1) / 3 + 1} ${d.year}"
            } ?: h.optString("period")
            val surprise = h.number("surprisePercent")?.let { if (kotlin.math.abs(it) <= 5) it * 100 else it }
                ?: estimate?.takeIf { it != 0.0 }?.let { (actual - it) / kotlin.math.abs(it) * 100 }
            EarningsResult(quarter, actual, estimate, surprise)
        }
        fun text(key: String) = profile?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
        if (profile == null && earnings.isEmpty()) return null
        return CompanyInfo(
            sector = text("sector"),
            industry = text("industry"),
            summary = text("longBusinessSummary"),
            website = text("website"),
            employees = profile?.number("fullTimeEmployees")?.toLong(),
            earnings = earnings,
        )
    }
}
