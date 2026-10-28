package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ExtendedHoursTest {

    private val ny = ZoneId.of("America/New_York")

    private fun chart(times: List<ZonedDateTime>, prices: List<Double>) = Chart(
        "X", times.map { it.toEpochSecond() }, prices, null,
        ChartMeta("X", null, null, null, null, null, null, null, null, null, null, null, 0, null, null, null),
    )

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ny)

    @Test
    fun `pre antes de la rueda y post despues, el mismo dia`() {
        val regular = chart(listOf(at(28, 9, 30), at(28, 12), at(28, 15, 55)), listOf(100.0, 101.0, 102.0))
        val extended = chart(
            listOf(at(28, 7), at(28, 8), at(28, 9, 30), at(28, 12), at(28, 15, 55), at(28, 17), at(28, 19)),
            listOf(99.0, 99.5, 100.0, 101.0, 102.0, 102.5, 103.0),
        )
        val (pre, post) = ExtendedHours.split(regular, extended)
        assertEquals(ExtSegment(listOf(99.0, 99.5), "Pre"), pre)
        assertEquals(ExtSegment(listOf(102.5, 103.0), "Post"), post)
    }

    @Test
    fun `a la manana, la pre-apertura de hoy va despues de la rueda de ayer`() {
        val regular = chart(listOf(at(27, 9, 30), at(27, 15, 55)), listOf(100.0, 101.0))
        val extended = chart(listOf(at(28, 6), at(28, 8)), listOf(101.5, 102.0))
        val (pre, post) = ExtendedHours.split(regular, extended)
        assertNull(pre)
        assertEquals("Pre", post?.label)
        assertEquals(listOf(101.5, 102.0), post?.prices)
    }

    @Test
    fun `sin datos fuera de horario no hay tramos`() {
        val regular = chart(listOf(at(28, 9, 30), at(28, 15, 55)), listOf(100.0, 101.0))
        assertEquals(null to null, ExtendedHours.split(regular, regular))
    }
}
