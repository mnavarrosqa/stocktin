package app.stocktin.data

import org.json.JSONArray
import org.json.JSONObject

/** Todo lo que cargaste a mano: listas, cartera, alertas y notas. */
data class BackupData(
    val lists: List<Watchlist>,
    val holdings: List<Holding>,
    val alerts: List<Alert>,
    val notes: Map<String, String>,
    val trades: List<Trade> = emptyList(),
    /** Lo que valía la cartera cada día, por moneda. */
    val history: Map<String, Map<String, Double>> = emptyMap(),
)

/** La copia de seguridad: un archivo JSON para guardar en el teléfono, en Drive o donde quieras. */
object Backup {

    private const val APP = "stocktin"
    private const val VERSION = 1

    fun toJson(data: BackupData, exportedAt: String): String = JSONObject()
        .put("app", APP)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("lists", JSONArray(Storage.listsToJson(data.lists)))
        .put("holdings", JSONArray(Portfolio.toJson(data.holdings)))
        .put("alerts", JSONArray(Alerts.toJson(data.alerts)))
        .put("notes", JSONObject(data.notes))
        .put("trades", JSONArray(Trades.toJson(data.trades)))
        .put("history", JSONObject(PortfolioHistory.toJson(data.history)))
        .toString(2)

    /** Lee una copia. Falla con un mensaje claro si el archivo no es de Stocktin. */
    fun fromJson(text: String): BackupData {
        val o = runCatching { JSONObject(text) }.getOrElse { error("El archivo no es una copia de Stocktin") }
        if (o.optString("app") != APP) error("El archivo no es una copia de Stocktin")
        val notes = o.optJSONObject("notes")
        return BackupData(
            lists = o.optJSONArray("lists")?.let { Storage.listsFromJson(it.toString()) }.orEmpty(),
            holdings = o.optJSONArray("holdings")?.let { Portfolio.fromJson(it.toString()) }.orEmpty(),
            alerts = o.optJSONArray("alerts")?.let { Alerts.fromJson(it.toString()) }.orEmpty(),
            notes = notes?.keys()?.asSequence()?.associateWith { notes.getString(it) }?.filterValues { it.isNotBlank() }.orEmpty(),
            trades = o.optJSONArray("trades")?.let { Trades.fromJson(it.toString()) }.orEmpty(),
            history = o.optJSONObject("history")?.let { PortfolioHistory.fromJson(it.toString()) }.orEmpty(),
        )
    }

    /** "3 listas, 5 posiciones, 2 alertas y 4 notas". */
    fun summary(data: BackupData): String {
        fun n(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
        val parts = listOf(
            n(data.lists.size, "lista", "listas"),
            n(data.holdings.size, "posición", "posiciones"),
            n(data.alerts.size, "alerta", "alertas"),
            n(data.notes.size, "nota", "notas"),
        ) + listOfNotNull(data.trades.size.takeIf { it > 0 }?.let { n(it, "operación", "operaciones") })
        return parts.dropLast(1).joinToString(", ") + " y " + parts.last()
    }
}
