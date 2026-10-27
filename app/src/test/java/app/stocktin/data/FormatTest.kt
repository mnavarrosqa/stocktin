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
}
