package app.stocktin.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * Listas y últimas cotizaciones, guardadas en archivos de la app para que los widgets muestren
 * algo aunque no haya conexión. La app y los widgets comparten esta misma instancia.
 */
class Repository private constructor(context: Context) {

    private val listsFile = File(context.filesDir, "watchlists.json")
    private val quotesFile = File(context.filesDir, "quotes.json")
    private val settings = Settings(context)
    private val refreshLock = Mutex()

    private val _lists = MutableStateFlow(loadLists())
    val lists: StateFlow<List<Watchlist>> = _lists.asStateFlow()

    private val _quotes = MutableStateFlow(loadQuotes())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastRefresh = MutableStateFlow(settings.lastRefresh)
    /** Cuándo se trajeron cotizaciones por última vez (milisegundos). */
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun list(id: String?): Watchlist? = _lists.value.firstOrNull { it.id == id } ?: _lists.value.firstOrNull()

    /** Todos los símbolos de todas las listas, sin repetir. */
    fun allSymbols(): List<String> = _lists.value.flatMap { it.symbols }.distinct()

    /** Trae las cotizaciones de [symbols] (o de todas las listas), de a una actualización por vez. */
    suspend fun refresh(symbols: List<String> = allSymbols()): Result<Unit> {
        if (symbols.isEmpty()) return Result.success(Unit)
        return refreshLock.withLock {
            _refreshing.value = true
            try {
                val fresh = YahooApi.quotes(symbols)
                _quotes.update { old -> old + fresh.associateBy { it.symbol } }
                saveQuotes()
                val now = System.currentTimeMillis()
                settings.lastRefresh = now
                _lastRefresh.value = now
                _lastError.value = null
                Result.success(Unit)
            } catch (e: Exception) {
                _lastError.value = e.message ?: "Sin conexión"
                Result.failure(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Guarda una cotización que llegó por otro lado (por ejemplo, al abrir el detalle). */
    fun putQuote(quote: Quote) {
        _quotes.update { it + (quote.symbol to quote) }
        saveQuotes()
    }

    fun createList(name: String): Watchlist {
        val list = Watchlist(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Lista" }, emptyList())
        editLists { it + list }
        return list
    }

    fun renameList(id: String, name: String) = editList(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun deleteList(id: String) = editLists { lists -> lists.filterNot { it.id == id }.ifEmpty { Storage.DEFAULT_LISTS } }

    fun addSymbol(listId: String, symbol: String) =
        editList(listId) { if (symbol in it.symbols) it else it.copy(symbols = it.symbols + symbol) }

    /** Deja los símbolos de la lista en el orden de [symbols] (después de arrastrar y soltar). */
    fun setOrder(listId: String, symbols: List<String>) = editList(listId) { list ->
        // Por las dudas, se conservan los que no vinieron y se descartan los que no estaban.
        val ordered = symbols.filter { it in list.symbols }
        list.copy(symbols = ordered + list.symbols.filterNot { it in ordered })
    }

    /** Vuelve a poner [symbol] en el lugar [index] (para deshacer un borrado). */
    fun insertSymbol(listId: String, symbol: String, index: Int) = editList(listId) {
        if (symbol in it.symbols) it else it.copy(symbols = it.symbols.toMutableList().apply { add(index.coerceIn(0, size), symbol) })
    }

    fun removeSymbol(listId: String, symbol: String) = editList(listId) { it.copy(symbols = it.symbols - symbol) }

    /** Mueve un símbolo [delta] lugares (negativo = para arriba). */
    fun moveSymbol(listId: String, symbol: String, delta: Int) = editList(listId) { list ->
        val from = list.symbols.indexOf(symbol)
        val to = (from + delta).coerceIn(0, list.symbols.lastIndex)
        if (from < 0 || from == to) return@editList list
        list.copy(symbols = list.symbols.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun moveList(id: String, delta: Int) = editLists { lists ->
        val from = lists.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, lists.lastIndex)
        if (from < 0 || from == to) lists else lists.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun editList(id: String, change: (Watchlist) -> Watchlist) =
        editLists { lists -> lists.map { if (it.id == id) change(it) else it } }

    @Synchronized
    private fun editLists(change: (List<Watchlist>) -> List<Watchlist>) {
        val updated = change(_lists.value)
        _lists.value = updated
        writeAtomically(listsFile, Storage.listsToJson(updated))
    }

    private fun loadLists(): List<Watchlist> =
        runCatching { Storage.listsFromJson(listsFile.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: Storage.DEFAULT_LISTS

    private fun loadQuotes(): Map<String, Quote> =
        runCatching { Storage.quotesFromJson(quotesFile.readText()) }.getOrDefault(emptyList()).associateBy { it.symbol }

    @Synchronized
    private fun saveQuotes() = writeAtomically(quotesFile, Storage.quotesToJson(_quotes.value.values))

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}
