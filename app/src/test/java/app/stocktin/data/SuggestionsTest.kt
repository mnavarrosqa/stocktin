package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {

    @Test
    fun `las sugeridas tienen ids unicos y simbolos sin repetir`() {
        assertEquals(SUGGESTED_LISTS.size, SUGGESTED_LISTS.map { it.id }.toSet().size)
        SUGGESTED_LISTS.forEach { assertEquals(it.name, it.symbols.size, it.symbols.toSet().size) }
    }

    @Test
    fun `no se sugiere una lista que ya armaste`() {
        val mine = listOf(Watchlist("x", " cripto ", emptyList()))
        val available = Suggestions.available(mine)
        assertFalse(available.any { it.id == "crypto" })
        assertTrue(available.any { it.id == "mag7" })
    }

    @Test
    fun `promedio de hoy con los que tienen cotizacion`() {
        val list = SuggestedList("t", "T", "", listOf("A", "B", "C"))
        fun q(s: String, p: Double) = Quote(s, s, 1.0, p, p, null, null, null)
        assertEquals(1.5, Suggestions.averageChange(list, mapOf("A" to q("A", 1.0), "B" to q("B", 2.0)))!!, 1e-9)
        assertEquals(null, Suggestions.averageChange(list, emptyMap()))
    }
}
