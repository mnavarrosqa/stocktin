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
