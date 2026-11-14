package app.stocktin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val data = BackupData(
        lists = listOf(Watchlist("a", "Mi lista", listOf("AAPL", "GGAL.BA"))),
        holdings = listOf(Holding("AAPL", 2.0, 150.0), Holding("MSFT.BA", 5.0, 19990.0)),
        alerts = listOf(Alert("x", "AAPL", 200.0, above = true, created = 1)),
        notes = mapOf("AAPL" to "Comprar si baja a 180\\ny vender a 260"),
    )

    @Test
    fun `ida y vuelta sin perder nada`() {
        val text = Backup.toJson(data, "2026-11-14T10:00:00-03:00")
        assertEquals(data, Backup.fromJson(text))
    }

    @Test
    fun `rechaza archivos que no son de stocktin`() {
        val error = runCatching { Backup.fromJson("""{"hola":1}""") }.exceptionOrNull()
        assertEquals("El archivo no es una copia de Stocktin", error?.message)
        assertTrue(runCatching { Backup.fromJson("no es json") }.isFailure)
    }

    @Test
    fun `resumen en criollo`() {
        assertEquals("1 lista, 2 posiciones, 1 alerta y 1 nota", Backup.summary(data))
    }
}
