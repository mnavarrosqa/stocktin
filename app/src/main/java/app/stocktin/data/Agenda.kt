package app.stocktin.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Qué clase de evento es (para el color y la etiqueta). */
enum class AgendaKind(val label: String) {
    FED("Fed"),
    CPI("Inflación"),
    JOBS("Empleo"),
    GDP("PBI"),
    EARNINGS("Balance"),
    OTHER("Dato"),
}

/**
 * Un evento de la agenda: un dato económico de EE.UU., una decisión de la Fed o el balance de un
 * símbolo que seguís.
 */
data class AgendaEvent(
    /** Milisegundos desde 1970. */
    val time: Long,
    val kind: AgendaKind,
    val title: String,
    val detail: String? = null,
    /** El símbolo, si es un balance. */
    val symbol: String? = null,
    /** Hora sin confirmar (balances con fecha estimada o sin hora). */
    val estimated: Boolean = false,
) {
    /** Para no avisar dos veces del mismo evento. */
    val key: String get() = "${kind.name}:${symbol ?: title}:$time"
}

object Agenda {

    private val NEW_YORK = ZoneId.of("America/New_York")

    /** Lee el calendario (`calendar.json`); las horas vienen en hora de Nueva York. */
    fun parse(json: String): List<AgendaEvent> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return (0 until events.length()).mapNotNull { i ->
            runCatching {
                val o = events.getJSONObject(i)
                val date = LocalDate.parse(o.getString("date"))
                val time = o.optString("time").takeIf { it.isNotBlank() }?.let(LocalTime::parse) ?: LocalTime.of(8, 30)
                AgendaEvent(
                    time = LocalDateTime.of(date, time).atZone(NEW_YORK).toInstant().toEpochMilli(),
                    kind = AgendaKind.entries.firstOrNull { it.name == o.optString("kind") } ?: AgendaKind.OTHER,
                    title = o.getString("title"),
                    detail = o.optString("detail").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
        }
    }

    /** Los balances que vienen de los símbolos que seguís. */
    fun earnings(quotes: Collection<Quote>): List<AgendaEvent> = quotes.mapNotNull { q ->
        val t = q.earningsTime ?: return@mapNotNull null
        // Las acciones de verdad: los ETF, índices y monedas no presentan balances.
        if (q.quoteType != null && q.quoteType != "EQUITY") return@mapNotNull null
        AgendaEvent(
            time = t * 1000,
            kind = AgendaKind.EARNINGS,
            title = "Balance de ${q.symbol}",
            detail = q.name,
            symbol = q.symbol,
            estimated = q.earningsEstimate,
        )
    }

    /**
     * Lo que viene: desde el principio de hoy (así se ven también los de hoy que ya pasaron) hasta
     * [days] días, ordenado por hora.
     */
    fun upcoming(events: List<AgendaEvent>, now: Long, days: Int = 30, zone: ZoneId = ZoneId.systemDefault()): List<AgendaEvent> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(days.toLong() + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return events.filter { it.time in from until to }.distinctBy { it.key }.sortedBy { it.time }
    }

    /** Los eventos de hoy de los que todavía no se avisó. */
    fun dueToday(events: List<AgendaEvent>, now: Long, notified: Set<String>, zone: ZoneId = ZoneId.systemDefault()): List<AgendaEvent> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return events.filter { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() == today && it.key !in notified }
    }
}

/** Para la hoja de un evento: qué es, por qué importa, qué mirar y qué buscar en las noticias. */
object AgendaInfo {

    fun explain(event: AgendaEvent): String = when (event.kind) {
        AgendaKind.FED ->
            "La Reserva Federal decide la tasa de interés de referencia de EE.UU. Primero sale el comunicado y " +
                "media hora después habla el presidente de la Fed. Si la tasa sube, baja o se mantiene (y lo que " +
                "insinúen para las próximas reuniones) mueve a las acciones, los bonos, el dólar y el cripto." +
                if (event.detail != null) " Esta reunión trae proyecciones económicas: dónde cree la Fed que van a estar las tasas, la inflación y el desempleo." else ""
        AgendaKind.CPI ->
            "El índice de precios al consumidor (CPI) mide cuánto subieron los precios en EE.UU. en el mes. Lo que más " +
                "se mira es la inflación \"núcleo\", sin alimentos ni energía. Si sale más alta de lo esperado, el " +
                "mercado espera tasas altas por más tiempo y las acciones suelen bajar; si sale más baja, al revés."
        AgendaKind.JOBS ->
            "El informe de empleo dice cuántos puestos de trabajo se crearon en el mes (las \"nóminas no agrícolas\"), " +
                "la tasa de desempleo y cuánto subieron los salarios. Un mercado laboral muy fuerte puede demorar las " +
                "bajas de tasas; uno muy débil hace temer una recesión."
        AgendaKind.GDP ->
            "El PBI mide cuánto creció la economía de EE.UU. en el trimestre (anualizado). Se publica en tres " +
                "estimaciones: la primera es la que más mueve al mercado. Un crecimiento fuerte es bueno para las " +
                "ganancias de las empresas, pero también puede mantener altas las tasas."
        AgendaKind.EARNINGS ->
            "La empresa presenta sus resultados del trimestre: ventas, ganancia por acción y lo que espera para " +
                "adelante. El precio suele moverse fuerte si los números o las proyecciones sorprenden frente a lo " +
                "que esperaban los analistas."
        AgendaKind.OTHER -> "Un dato económico de EE.UU. que puede mover al mercado."
    }

    /** Qué mirar cuando sale: símbolo y nombre. */
    fun watch(event: AgendaEvent): List<Pair<String, String>> = when (event.kind) {
        AgendaKind.EARNINGS -> listOfNotNull(event.symbol?.let { it to (event.detail ?: it) }, "^GSPC" to "S&P 500")
        AgendaKind.FED -> listOf("^GSPC" to "S&P 500", "^TNX" to "Bono 10 años", "DX-Y.NYB" to "Índice dólar", "BTC-USD" to "Bitcoin")
        else -> listOf("^GSPC" to "S&P 500", "^NDX" to "Nasdaq 100", "^TNX" to "Bono 10 años", "DX-Y.NYB" to "Índice dólar")
    }

    /** Qué buscar en las noticias de Yahoo. */
    fun newsQuery(event: AgendaEvent): String = when (event.kind) {
        AgendaKind.FED -> "Federal Reserve interest rate decision"
        AgendaKind.CPI -> "CPI inflation report"
        AgendaKind.JOBS -> "jobs report nonfarm payrolls"
        AgendaKind.GDP -> "US GDP report"
        AgendaKind.EARNINGS -> event.symbol ?: event.title
        AgendaKind.OTHER -> event.title
    }
}
