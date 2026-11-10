package app.stocktin.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** El último resultado de un dato económico, listo para mostrar. */
data class EconResult(
    val kind: AgendaKind,
    /** Lo principal, por ejemplo "3.0% anual" o "+150 mil empleos". */
    val headline: String,
    /** De qué mes o trimestre es, por ejemplo "Octubre 2026" o "3.er trimestre 2026". */
    val periodLabel: String,
    /** yyyy-MM del dato (en el PBI, el primer mes del trimestre). */
    val period: String,
    /** Otros números: nombre y valor. */
    val details: List<Pair<String, String>>,
    /** Los últimos valores para un gráfico de barras: etiqueta corta y valor. */
    val history: List<Pair<String, Double>>,
    /** Qué muestran las barras. */
    val historyLabel: String,
    /** Lo que se muestra en una línea al lado del evento en la agenda. */
    val short: String,
    /** El último día con datos (yyyy-MM-dd). */
    val asOf: String = period,
)

/** Los datos de EE.UU. de FRED (Reserva Federal de St. Louis), que no piden clave. */
object EconData {

    /** Las series de FRED que hacen falta para cada clase de dato. */
    val SERIES: Map<AgendaKind, List<String>> = mapOf(
        AgendaKind.CPI to listOf("CPIAUCSL", "CPILFESL"),
        AgendaKind.JOBS to listOf("PAYEMS", "UNRATE"),
        AgendaKind.GDP to listOf("A191RL1Q225SBEA"),
        AgendaKind.FED to listOf("DFEDTARL", "DFEDTARU"),
    )

    private val spanish = Locale.forLanguageTag("es-AR")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", spanish)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", spanish)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", spanish)

    /** Lee el CSV de FRED ("observation_date,SERIE" o "DATE,SERIE"); los "." son datos que faltan. */
    fun parseCsv(text: String): List<Pair<LocalDate, Double>> =
        text.lineSequence().drop(1).mapNotNull { line ->
            val parts = line.trim().split(',')
            if (parts.size < 2) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val value = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            date to value
        }.toList()

    private fun pct(value: Double, decimals: Int = 1) = String.format(Locale.US, "%.${decimals}f%%", value)
    private fun signedPct(value: Double, decimals: Int = 1) = (if (value >= 0) "+" else "") + pct(value, decimals)
    private fun period(date: LocalDate) = date.toString().take(7)
    private fun monthLabel(date: LocalDate) = monthYear.format(date).replaceFirstChar { it.uppercase() }
    private fun shortLabel(date: LocalDate) = shortMonth.format(date).replace(".", "")

    /** Inflación: anual y mensual del índice general y anual del núcleo (sin alimentos ni energía). */
    fun cpi(all: List<Pair<LocalDate, Double>>, core: List<Pair<LocalDate, Double>>): EconResult? {
        val byDate = all.toMap()
        fun yoy(date: LocalDate) = byDate[date.minusYears(1)]?.let { (byDate.getValue(date) / it - 1) * 100 }
        val dates = all.map { it.first }.filter { yoy(it) != null }
        val last = dates.lastOrNull() ?: return null
        val annual = yoy(last) ?: return null
        val previous = dates.getOrNull(dates.size - 2)?.let(::yoy)
        val monthly = byDate[last.minusMonths(1)]?.let { (byDate.getValue(last) / it - 1) * 100 }
        val coreMap = core.toMap()
        val coreAnnual = coreMap[last]?.let { v -> coreMap[last.minusYears(1)]?.let { (v / it - 1) * 100 } }
        return EconResult(
            kind = AgendaKind.CPI,
            headline = "${pct(annual)} anual",
            periodLabel = monthLabel(last),
            period = period(last),
            details = listOfNotNull(
                monthly?.let { "Mensual" to signedPct(it) },
                coreAnnual?.let { "Núcleo (anual)" to pct(it) },
                previous?.let { "Mes anterior" to pct(it) },
            ),
            history = dates.takeLast(12).map { shortLabel(it) to (yoy(it) ?: 0.0) },
            historyLabel = "Inflación anual, mes a mes",
            short = "${pct(annual)} anual",
        )
    }

    /** Empleo: puestos creados en el mes (nóminas no agrícolas) y tasa de desempleo. */
    fun jobs(payrolls: List<Pair<LocalDate, Double>>, unemployment: List<Pair<LocalDate, Double>>): EconResult? {
        if (payrolls.size < 3) return null
        val changes = payrolls.zipWithNext { a, b -> b.first to (b.second - a.second) }
        val (last, created) = changes.last()
        val previous = changes.getOrNull(changes.size - 2)?.second
        val rates = unemployment.toMap()
        fun thousands(v: Double) = (if (v >= 0) "+" else "−") + String.format(Locale.US, "%.0f", abs(v)) + " mil"
        return EconResult(
            kind = AgendaKind.JOBS,
            headline = "${thousands(created)} empleos",
            periodLabel = monthLabel(last),
            period = period(last),
            details = listOfNotNull(
                rates[last]?.let { "Desempleo" to pct(it) },
                rates[last.minusMonths(1)]?.let { "Desempleo anterior" to pct(it) },
                previous?.let { "Mes anterior" to "${thousands(it)} empleos" },
            ),
            history = changes.takeLast(12).map { shortLabel(it.first) to it.second },
            historyLabel = "Empleos creados por mes (miles)",
            short = "${thousands(created)} empleos",
        )
    }

    /** PBI: crecimiento del trimestre, anualizado. */
    fun gdp(growth: List<Pair<LocalDate, Double>>): EconResult? {
        val (last, value) = growth.lastOrNull() ?: return null
        val quarter = (last.monthValue - 1) / 3 + 1
        fun label(d: LocalDate) = "T${(d.monthValue - 1) / 3 + 1} ${d.year % 100}"
        val ordinal = listOf("1.er", "2.º", "3.er", "4.º")[quarter - 1]
        return EconResult(
            kind = AgendaKind.GDP,
            headline = "${signedPct(value)} anualizado",
            periodLabel = "$ordinal trimestre ${last.year}",
            period = period(last),
            details = listOfNotNull(growth.getOrNull(growth.size - 2)?.let { "Trimestre anterior" to signedPct(it.second) }),
            history = growth.takeLast(8).map { label(it.first) to it.second },
            historyLabel = "Crecimiento por trimestre, anualizado",
            short = "${signedPct(value)} anualizado",
        )
    }

    /** Tasa de la Fed: el rango vigente y la última vez que cambió. */
    fun fed(lower: List<Pair<LocalDate, Double>>, upper: List<Pair<LocalDate, Double>>): EconResult? {
        val (today, top) = upper.lastOrNull() ?: return null
        val bottom = lower.toMap()[today] ?: (top - 0.25)
        val range = "${pct(bottom, 2)} – ${pct(top, 2)}"
        // Los días en que cambió el techo del rango.
        val changes = upper.zipWithNext().filter { (a, b) -> b.second != a.second }.map { (a, b) -> b.first to (b.second - a.second) }
        val details = changes.lastOrNull()?.let { (date, delta) ->
            val verb = if (delta > 0) "Subió" else "Bajó"
            listOf("Último cambio" to "$verb ${String.format(Locale.US, "%.2f", abs(delta))} el ${dayMonthYear.format(date)}")
        }.orEmpty()
        // Una barra por mes: el techo del rango a fin de mes, los últimos 2 años.
        val monthly = upper.groupBy { period(it.first) }.map { (_, v) -> v.last() }.takeLast(24)
        return EconResult(
            kind = AgendaKind.FED,
            headline = range,
            periodLabel = "Vigente desde el ${changes.lastOrNull()?.first?.let { dayMonthYear.format(it) } ?: "último cambio"}",
            period = period(today),
            details = details,
            history = monthly.map { shortLabel(it.first) to it.second },
            historyLabel = "Techo de la tasa, mes a mes (2 años)",
            short = "tasa $range",
            asOf = today.toString(),
        )
    }

    /** El resultado ya salió para este evento: es del mes que informa (la Fed, siempre el vigente). */
    fun matches(event: AgendaEvent, result: EconResult?): Boolean = when {
        result == null || result.kind != event.kind -> false
        // La Fed: el rango ya tiene datos del día de la reunión en adelante.
        event.kind == AgendaKind.FED ->
            result.asOf >= java.time.Instant.ofEpochMilli(event.time).atZone(java.time.ZoneId.of("America/New_York")).toLocalDate().toString()
        else -> event.period == result.period
    }
}
