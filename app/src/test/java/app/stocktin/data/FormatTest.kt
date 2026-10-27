package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {

    @Test
    fun `precios como en Yahoo`() {
        assertEquals("80,227.92", Format.price(80227.921))
        assertEquals("17.72", Format.price(17.72))
        assertEquals("0.5432", Format.price(0.54321))
    }

    @Test
    fun `porcentajes con signo`() {
        assertEquals("+1.11%", Format.percent(1.111))
        assertEquals("-3.64%", Format.percent(-3.64))
        assertEquals("+0.00%", Format.percent(-0.001))
    }

    @Test
    fun `numeros grandes abreviados`() {
        assertEquals("5.2T", Format.compact(5.2e12))
        assertEquals("1.23M", Format.compact(1_234_567.0))
        assertEquals("950", Format.compact(950.0))
    }

    @Test
    fun `horario de la bolsa`() {
        val ny = ZoneId.of("America/New_York")
        assertTrue(MarketHours.isUsActive(ZonedDateTime.of(2026, 10, 27, 8, 42, 0, 0, ny)))
        assertFalse(MarketHours.isUsActive(ZonedDateTime.of(2026, 10, 27, 21, 0, 0, 0, ny)))
        assertFalse(MarketHours.isUsActive(ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, ny)))
    }

    @Test
    fun `listas y cotizaciones se guardan y se leen igual`() {
        val lists = listOf(Watchlist("a", "USD", listOf("SPY", "^VIX")))
        assertEquals(lists, Storage.listsFromJson(Storage.listsToJson(lists)))
        val quote = Quote(
            symbol = "SPY", name = "SPDR", price = 765.37, change = 5.4, changePercent = 0.71,
            previousClose = 759.97, currency = "USD", marketState = "PRE", extPrice = 768.1,
            extChangePercent = 0.36, extSession = ExtSession.PRE, spark = listOf(1.0, 2.0), volume = 123L, time = 99,
        )
        assertEquals(listOf(quote), Storage.quotesFromJson(Storage.quotesToJson(listOf(quote))))
    }

    @Test
    fun `cuanto falta para que abra o cierre wall street`() {
        val ny = ZoneId.of("America/New_York")
        // Martes 11:00: abierto, cierra a las 16:00.
        val open = MarketHours.status(ZonedDateTime.of(2026, 10, 27, 11, 0, 0, 0, ny))
        assertEquals(MarketHours.Phase.OPEN, open.phase)
        assertEquals(16, open.next.hour)
        // Martes 8:00: pre-apertura, abre hoy 9:30.
        val pre = MarketHours.status(ZonedDateTime.of(2026, 10, 27, 8, 0, 0, 0, ny))
        assertEquals(MarketHours.Phase.PRE, pre.phase)
        assertEquals(27, pre.next.dayOfMonth)
        assertEquals(9, pre.next.hour)
        // Viernes 21:00: cerrado, abre el lunes.
        val friday = MarketHours.status(ZonedDateTime.of(2026, 10, 30, 21, 0, 0, 0, ny))
        assertEquals(MarketHours.Phase.CLOSED, friday.phase)
        assertEquals(java.time.DayOfWeek.MONDAY, friday.next.dayOfWeek)
        assertEquals("3 h 12 min", MarketHours.countdown(open.next.minusMinutes(192), open.next))
        assertEquals("45 min", MarketHours.countdown(open.next.minusMinutes(45), open.next))
    }

    @Test
    fun `screener de yahoo`() {
        val json = """{"finance":{"result":[{"quotes":[{"symbol":"ABC","shortName":"Abc Inc","regularMarketPrice":10.5,"regularMarketChangePercent":12.3,"regularMarketChange":1.15}]}]}}"""
        val q = YahooParser.parseScreener(json).single()
        assertEquals("ABC", q.symbol)
        assertEquals(12.3, q.changePercent, 1e-9)
    }
}
