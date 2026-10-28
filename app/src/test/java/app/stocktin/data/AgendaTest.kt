package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class AgendaTest {

    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun `lee el calendario en hora de Nueva York`() {
        val json = """{"events":[{"date":"2026-12-09","time":"14:00","kind":"FED","title":"Decisión de tasas de la Fed","detail":"Con proyecciones"}]}"""
        val event = Agenda.parse(json).single()
        assertEquals(AgendaKind.FED, event.kind)
        assertEquals("Con proyecciones", event.detail)
        // 14:00 en Nueva York (invierno) son las 16:00 en Buenos Aires.
        assertEquals(ZonedDateTime.of(2026, 12, 9, 16, 0, 0, 0, buenosAires).toInstant().toEpochMilli(), event.time)
    }

    @Test
    fun `el calendario que viene con la app se puede leer y esta ordenado`() {
        val file = listOf("src/main/assets/calendar.json", "app/src/main/assets/calendar.json").map(::File).first { it.exists() }
        val events = Agenda.parse(file.readText())
        assertTrue(events.size >= 20)
        assertEquals(events.sortedBy { it.time }, events)
    }

    @Test
    fun `balances solo de acciones y lo que viene ordenado desde hoy`() {
        val now = ZonedDateTime.of(2026, 10, 28, 10, 0, 0, 0, buenosAires)
        fun q(symbol: String, type: String, at: ZonedDateTime?) =
            Quote(symbol, symbol, 1.0, 0.0, 0.0, null, null, null, quoteType = type, earningsTime = at?.toEpochSecond())
        val earnings = Agenda.earnings(
            listOf(
                q("AAPL", "EQUITY", now.plusDays(2)),
                q("SPY", "ETF", now.plusDays(1)),
                q("MSFT", "EQUITY", now.minusDays(3)),
                q("NVDA", "EQUITY", null),
            ),
        )
        assertEquals(listOf("AAPL", "MSFT"), earnings.map { it.symbol })
        val fed = AgendaEvent(now.withHour(15).toInstant().toEpochMilli(), AgendaKind.FED, "Fed")
        val upcoming = Agenda.upcoming(earnings + fed, now.toInstant().toEpochMilli(), zone = buenosAires)
        assertEquals(listOf("Fed", "Balance de AAPL"), upcoming.map { it.title })
        // Hoy: solo la Fed, y no de nuevo si ya se avisó.
        val nowMs = now.toInstant().toEpochMilli()
        assertEquals(listOf("Fed"), Agenda.dueToday(upcoming, nowMs, emptySet(), buenosAires).map { it.title })
        assertEquals(emptyList<String>(), Agenda.dueToday(upcoming, nowMs, setOf(fed.key), buenosAires).map { it.title })
    }

    @Test
    fun `la fecha del balance sale de la cotizacion de Yahoo`() {
        val body = """{"quoteResponse":{"result":[{"symbol":"AAPL","regularMarketPrice":250.0,"quoteType":"EQUITY",
            "earningsTimestamp":1761854400,"earningsTimestampStart":1761854400,"earningsTimestampEnd":1761854400,
            "isEarningsDateEstimate":false}]}}"""
        val quote = YahooParser.parseQuotes(body).single()
        assertEquals(1761854400L, quote.earningsTime)
        assertEquals(false, quote.earningsEstimate)
        assertEquals(quote, Storage.quotesFromJson(Storage.quotesToJson(listOf(quote))).single())
    }
}
