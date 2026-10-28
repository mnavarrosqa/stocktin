package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartCompareTest {

    private fun chart(times: List<Long>, prices: List<Double>, previousClose: Double? = null) =
        Chart("X", times, prices, previousClose, ChartMeta("X", null, null, previousClose, null, null, null, null, null, null, null, null, 0, null, null, null))

    @Test
    fun `indice segun el simbolo`() {
        assertEquals("^GSPC", ChartCompare.benchmarkFor("NVDA")?.first)
        assertEquals("^MERV", ChartCompare.benchmarkFor("GGAL.BA")?.first)
        assertNull(ChartCompare.benchmarkFor("^GSPC"))
    }

    @Test
    fun `la otra serie se alinea a los horarios y a la escala del simbolo`() {
        val base = chart(listOf(10, 20, 30, 40), listOf(100.0, 101.0, 102.0, 103.0))
        // El índice tiene otros horarios: en 20 vale lo de 15, en 40 lo de 35.
        val other = chart(listOf(5, 15, 35), listOf(5000.0, 5050.0, 5100.0))
        val overlay = ChartCompare.overlay(base, 100.0, other, otherStart = null)
        assertEquals(listOf(100.0, 101.0, 101.0, 102.0), overlay.map { Math.round(it * 1000) / 1000.0 })
        assertEquals(2.0, ChartCompare.percent(overlay, 100.0)!!, 1e-9)
        assertEquals(1.0, ChartCompare.percent(overlay, 100.0, index = 1)!!, 1e-9)
    }

    @Test
    fun `en el dia arranca desde el cierre anterior del indice`() {
        val base = chart(listOf(10, 20), listOf(100.0, 101.0))
        val other = chart(listOf(10, 20), listOf(4950.0, 5100.0), previousClose = 5000.0)
        val overlay = ChartCompare.overlay(base, 99.0, other, otherStart = 5000.0)
        assertEquals(99.0 * 4950 / 5000, overlay[0], 1e-9)
        assertEquals(2.0, ChartCompare.percent(overlay, 99.0)!!, 1e-9)
    }
}
