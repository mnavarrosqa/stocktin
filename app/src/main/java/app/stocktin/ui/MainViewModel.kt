package app.stocktin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stocktin.data.Alert
import app.stocktin.data.Chart
import app.stocktin.data.Format
import app.stocktin.data.ChartCompare
import app.stocktin.data.ChartRange
import app.stocktin.data.MARKET_SYMBOLS
import app.stocktin.data.Movers
import app.stocktin.data.Quote
import app.stocktin.data.STRIP_SYMBOLS
import app.stocktin.data.NewsItem
import app.stocktin.data.SortMode
import app.stocktin.data.Repository
import app.stocktin.data.SearchResult
import app.stocktin.data.Settings
import app.stocktin.data.YahooApi
import app.stocktin.update.Release
import app.stocktin.update.Updater
import app.stocktin.widget.RefreshScheduler
import app.stocktin.widget.Widgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Pestañas de la barra de abajo. */
enum class Tab(val label: String) {
    LISTS("Listas"),
    MARKETS("Mercados"),
    SETTINGS("Ajustes"),
}

/** Pantallas que se abren encima de las pestañas. */
sealed interface Screen {
    data object Search : Screen
    data class Detail(val symbol: String) : Screen
}

sealed interface UpdateState {
    val release: Release

    data class Available(override val release: Release) : UpdateState
    /** Bajando el APK; [progress] es null mientras no se sabe el tamaño. */
    data class Downloading(override val release: Release, val progress: Float?) : UpdateState
    /** Bajada: Android está mostrando "¿Actualizar Stocktin?". */
    data class Installing(override val release: Release) : UpdateState
}

/** Un aviso abajo de la pantalla, con un botón opcional (por ejemplo, Deshacer). */
data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class ChartState(
    val symbol: String,
    val range: ChartRange = ChartRange.DAY,
    val chart: Chart? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** El índice de referencia en el mismo rango, si se está comparando. */
    val benchmark: Chart? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = Repository.get(app)
    private val settings = Settings(app)
    private val updater = Updater(app)

    val lists = repository.lists
    val quotes = repository.quotes
    val alerts = repository.alerts
    val holdings = repository.holdings
    val refreshing = repository.refreshing
    val lastRefresh = repository.lastRefresh
    val lastError = repository.lastError

    private val _currentList = MutableStateFlow(repository.list(settings.currentList)?.id ?: "")
    val currentList: StateFlow<String> = _currentList.asStateFlow()

    private val _tab = MutableStateFlow(Tab.LISTS)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    /** Pantallas abiertas encima de la pestaña (vacía = solo la pestaña). */
    private val _stack = MutableStateFlow<List<Screen>>(emptyList())
    val screen: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    private val _chart = MutableStateFlow<ChartState?>(null)
    val chart: StateFlow<ChartState?> = _chart.asStateFlow()
    private var chartJob: Job? = null

    private val _showValue = MutableStateFlow(settings.showChangeValue)
    val showValue: StateFlow<Boolean> = _showValue.asStateFlow()

    private val _refreshMinutes = MutableStateFlow(settings.refreshMinutes)
    val refreshMinutes: StateFlow<Int> = _refreshMinutes.asStateFlow()

    private val _saveBattery = MutableStateFlow(settings.saveBatteryWhenClosed)
    val saveBattery: StateFlow<Boolean> = _saveBattery.asStateFlow()

    private val _autoCheckUpdates = MutableStateFlow(settings.autoCheckUpdates)
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _update = MutableStateFlow<UpdateState?>(null)
    val update: StateFlow<UpdateState?> = _update.asStateFlow()

    private val _checkingUpdates = MutableStateFlow(false)
    val checkingUpdates: StateFlow<Boolean> = _checkingUpdates.asStateFlow()

    /** Mostrar el aviso de versión nueva (solo la primera vez que se encuentra). */
    private val _lastUpdateCheck = MutableStateFlow(settings.lastUpdateCheck)
    val lastUpdateCheck: StateFlow<Long> = _lastUpdateCheck.asStateFlow()

    /** Novedades de la versión recién instalada, para mostrarlas una vez. */
    private val _whatsNew = MutableStateFlow<Release?>(null)
    val whatsNew: StateFlow<Release?> = _whatsNew.asStateFlow()

    /** Historial de versiones (null mientras carga). */
    private val _releases = MutableStateFlow<List<Release>?>(null)
    val releases: StateFlow<List<Release>?> = _releases.asStateFlow()

    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(settings.dynamicColor)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private var downloadJob: Job? = null

    private val _updateDialog = MutableStateFlow(false)
    val updateDialog: StateFlow<Boolean> = _updateDialog.asStateFlow()

    val installedVersion: String = runCatching { updater.installedVersionName }.getOrDefault("")

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.from(settings.sortMode))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /** Noticias del símbolo abierto (null mientras cargan). */
    private val _news = MutableStateFlow<Pair<String, List<NewsItem>?>?>(null)
    val news: StateFlow<Pair<String, List<NewsItem>?>?> = _news.asStateFlow()
    private var newsJob: Job? = null

    /** Las que más suben, bajan o se operan hoy (vacío mientras cargan). */
    private val _movers = MutableStateFlow<Map<Movers, List<Quote>>>(emptyMap())
    val movers: StateFlow<Map<Movers, List<Quote>>> = _movers.asStateFlow()

    private val _moversKind = MutableStateFlow(Movers.GAINERS)
    val moversKind: StateFlow<Movers> = _moversKind.asStateFlow()

    /** La hora, para las cuentas regresivas (se renueva cada pocos segundos). */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _trending = MutableStateFlow<List<String>>(emptyList())
    val trending: StateFlow<List<String>> = _trending.asStateFlow()

    private val _recent = MutableStateFlow(settings.recentSearches)
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    /** La app está a la vista (entre onStart y onStop). */
    @Volatile
    private var foreground = false

    /** Al volver a la app se actualiza enseguida, sin esperar la próxima vuelta del ciclo. */
    fun setForeground(visible: Boolean) {
        foreground = visible
        // Si se volvió del instalador sin actualizar (se canceló), se puede volver a intentar.
        val state = _update.value
        if (visible && state is UpdateState.Installing) _update.value = UpdateState.Available(state.release)
        if (visible && System.currentTimeMillis() - lastAttempt > LIVE_REFRESH_MS) {
            refresh(quiet = true)
            if (_tab.value == Tab.MARKETS) refreshMarkets()
        }
    }

    private var lastAttempt = 0L

    init {
        RefreshScheduler.schedule(app)
        afterUpdate()
        if (settings.autoCheckUpdates && System.currentTimeMillis() - settings.lastUpdateCheck > UPDATE_CHECK_INTERVAL_MS) {
            checkForUpdates(manual = false)
        }
        // Con la app a la vista, los precios se actualizan solos cada medio minuto.
        viewModelScope.launch {
            while (isActive) {
                if (foreground && System.currentTimeMillis() - lastAttempt > LIVE_REFRESH_MS) {
                    refresh(quiet = true)
                    if (_tab.value == Tab.MARKETS) refreshMarkets()
                }
                _now.value = System.currentTimeMillis()
                delay(5_000)
            }
        }
    }

    fun refresh(quiet: Boolean = false) {
        lastAttempt = System.currentTimeMillis()
        viewModelScope.launch {
            val result = Widgets.refresh(getApplication(), extra = STRIP_SYMBOLS.map { it.first })
            if (!quiet && result.isFailure) showMessage("No se pudo actualizar. Revisá la conexión.")
        }
    }

    fun refreshMarkets() {
        viewModelScope.launch { repository.refresh(MARKET_SYMBOLS) }
        loadMovers(_moversKind.value)
    }

    fun setMoversKind(kind: Movers) {
        _moversKind.value = kind
        if (_movers.value[kind] == null) loadMovers(kind)
    }

    private fun loadMovers(kind: Movers) {
        viewModelScope.launch {
            runCatching { YahooApi.movers(kind) }.onSuccess { list ->
                _movers.update { it + (kind to list) }
                repository.putQuotes(list)
            }
        }
    }

    // Navegación

    fun selectTab(tab: Tab) {
        _tab.value = tab
        _stack.value = emptyList()
        if (tab == Tab.MARKETS && (MARKET_SYMBOLS.any { repository.quotes.value[it] == null } || _movers.value.isEmpty())) {
            refreshMarkets()
        }
    }

    fun open(screen: Screen) {
        // Un detalle reemplaza al anterior en vez de apilarse (en pantallas anchas se ve al lado).
        _stack.update { stack -> (if (screen is Screen.Detail) stack.filterNot { it is Screen.Detail } else stack) + screen }
        if (screen is Screen.Detail) loadDetail(screen.symbol)
        if (screen is Screen.Search) {
            _query.value = ""
            _results.value = emptyList()
            if (_trending.value.isEmpty()) loadTrending()
        }
    }

    /** Vuelve atrás; devuelve false si ya estaba en el inicio. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.isEmpty()) {
            if (_tab.value == Tab.LISTS) return false
            _tab.value = Tab.LISTS
            return true
        }
        _stack.value = stack.dropLast(1)
        (stack.dropLast(1).lastOrNull() as? Screen.Detail)?.let { loadDetail(it.symbol) }
        return true
    }

    /** Desde un widget: abre el detalle de [symbol] encima de las listas. */
    fun openFromWidget(symbol: String) {
        _tab.value = Tab.LISTS
        _stack.value = emptyList()
        open(Screen.Detail(symbol))
    }

    // Listas

    fun selectList(id: String) {
        _currentList.value = id
        settings.currentList = id
    }

    fun createList(name: String) {
        val list = repository.createList(name)
        selectList(list.id)
        widgetsChanged()
    }

    fun renameList(id: String, name: String) {
        repository.renameList(id, name)
        widgetsChanged()
    }

    fun deleteList(id: String) {
        repository.deleteList(id)
        if (_currentList.value == id) selectList(repository.lists.value.first().id)
        widgetsChanged()
    }

    fun moveList(id: String, delta: Int) = repository.moveList(id, delta)

    fun toggleSymbol(symbol: String, listId: String = _currentList.value) {
        val list = repository.list(listId) ?: return
        if (symbol in list.symbols) {
            removeSymbol(symbol, list.id)
        } else {
            repository.addSymbol(list.id, symbol)
            rememberSearch(symbol)
            if (repository.quotes.value[symbol] == null) {
                viewModelScope.launch { repository.refresh(listOf(symbol)) }
            }
            _message.value = Message("$symbol agregado a ${list.name}", "Deshacer") {
                repository.removeSymbol(list.id, symbol)
                widgetsChanged()
            }
            widgetsChanged()
        }
    }

    /** Saca [symbol] de la lista, con la opción de deshacerlo. */
    fun removeSymbol(symbol: String, listId: String = _currentList.value) {
        val list = repository.list(listId) ?: return
        val index = list.symbols.indexOf(symbol).takeIf { it >= 0 } ?: return
        repository.removeSymbol(list.id, symbol)
        _message.value = Message("$symbol quitado de ${list.name}", "Deshacer") {
            repository.insertSymbol(list.id, symbol, index)
            widgetsChanged()
        }
        widgetsChanged()
    }

    /** Nuevo orden de la lista actual, arrastrando y soltando: pasa a orden personalizado. */
    fun setOrder(symbols: List<String>) {
        repository.setOrder(_currentList.value, symbols)
        setSortMode(SortMode.CUSTOM)
        widgetsChanged()
    }

    fun setSortMode(mode: SortMode) {
        settings.sortMode = mode.name
        _sortMode.value = mode
    }

    /** Guarda [symbol] entre las búsquedas recientes. */
    fun rememberSearch(symbol: String) {
        val updated = (listOf(symbol) + _recent.value.filterNot { it == symbol }).take(8)
        settings.recentSearches = updated
        _recent.value = updated
    }

    fun clearRecent() {
        settings.recentSearches = emptyList()
        _recent.value = emptyList()
    }

    private fun loadTrending() {
        viewModelScope.launch {
            val symbols = runCatching { YahooApi.trending() }.getOrDefault(emptyList())
            _trending.value = symbols
            repository.refresh(symbols.filter { repository.quotes.value[it] == null })
        }
    }

    fun moveSymbol(symbol: String, delta: Int) {
        repository.moveSymbol(_currentList.value, symbol, delta)
        widgetsChanged()
    }

    private fun widgetsChanged() {
        viewModelScope.launch { Widgets.updateAll(getApplication()) }
    }

    // Búsqueda

    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _searching.value = true
            _results.value = runCatching { YahooApi.search(text) }.getOrElse { emptyList() }
            _searching.value = false
        }
    }

    // Detalle

    fun setRange(range: ChartRange) {
        val symbol = _chart.value?.symbol ?: return
        loadChart(symbol, range)
    }

    private fun loadDetail(symbol: String) {
        if (_news.value?.first != symbol) {
            newsJob?.cancel()
            _news.value = symbol to null
            newsJob = viewModelScope.launch {
                _news.value = symbol to runCatching { YahooApi.news(symbol) }.getOrDefault(emptyList())
            }
        }
        loadChart(symbol, _chart.value?.takeIf { it.symbol == symbol }?.range ?: ChartRange.DAY)
        if (repository.quotes.value[symbol] == null) {
            viewModelScope.launch { repository.refresh(listOf(symbol)) }
        }
    }

    private val _compare = MutableStateFlow(false)
    /** Comparar el gráfico del detalle con su índice (S&P 500 o Merval). */
    val compare: StateFlow<Boolean> = _compare.asStateFlow()

    fun setCompare(enabled: Boolean) {
        _compare.value = enabled
        val state = _chart.value ?: return
        loadChart(state.symbol, state.range)
    }

    private fun loadChart(symbol: String, range: ChartRange) {
        chartJob?.cancel()
        val old = _chart.value?.takeIf { it.symbol == symbol && it.range == range }
        _chart.value = ChartState(symbol, range, chart = old?.chart, benchmark = old?.benchmark)
        val benchmark = ChartCompare.benchmarkFor(symbol)?.first?.takeIf { _compare.value }
        chartJob = viewModelScope.launch {
            val other = benchmark?.let { async { runCatching { YahooApi.chart(it, range) }.getOrNull() } }
            _chart.value = runCatching { YahooApi.chart(symbol, range) }.fold(
                onSuccess = { ChartState(symbol, range, it, loading = false, benchmark = other?.await()) },
                onFailure = { ChartState(symbol, range, null, loading = false, error = "No se pudo cargar el gráfico") },
            )
        }
    }

    // Ajustes

    fun setShowValue(value: Boolean) {
        settings.showChangeValue = value
        _showValue.value = value
    }

    fun setRefreshMinutes(minutes: Int) {
        settings.refreshMinutes = minutes
        _refreshMinutes.value = minutes
        RefreshScheduler.schedule(getApplication())
    }

    fun setSaveBattery(enabled: Boolean) {
        settings.saveBatteryWhenClosed = enabled
        _saveBattery.value = enabled
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        settings.autoCheckUpdates = enabled
        _autoCheckUpdates.value = enabled
    }

    /** Crea una alerta para cuando [symbol] llegue a [target] (sube o baja según el precio de ahora). */
    fun addAlert(symbol: String, target: Double) {
        val price = repository.quotes.value[symbol]?.price ?: return
        val alert = repository.addAlert(symbol, target, price)
        showMessage("Te avisamos cuando $symbol ${if (alert.above) "suba" else "baje"} a ${Format.price(target)}")
    }

    fun addMoveAlert(symbol: String, percent: Double) {
        repository.addMoveAlert(symbol, percent)
        showMessage("Te avisamos cuando $symbol se mueva más de ${Format.percent(percent).removePrefix("+")} en un día")
    }

    fun removeAlert(alert: Alert) {
        repository.removeAlert(alert.id)
        _message.value = Message("Alerta de ${alert.symbol} borrada", "Deshacer") {
            repository.restoreAlert(alert)
        }
    }

    fun setHolding(symbol: String, shares: Double, cost: Double) {
        repository.setHolding(symbol, shares, cost)
        widgetsChanged()
    }

    fun removeHolding(symbol: String) {
        val old = repository.holdings.value[symbol] ?: return
        repository.removeHolding(symbol)
        _message.value = Message("$symbol salió de tu cartera", "Deshacer") {
            repository.setHolding(old.symbol, old.shares, old.cost)
        }
    }

    fun showMessage(text: String) {
        _message.value = Message(text)
    }

    fun messageShown() {
        _message.value = null
    }

    // Actualizaciones de la app

    fun checkForUpdates(manual: Boolean) {
        if (_checkingUpdates.value) return
        _checkingUpdates.value = true
        viewModelScope.launch {
            runCatching { updater.checkForUpdate() }
                .onSuccess { release ->
                    settings.lastUpdateCheck = System.currentTimeMillis()
                    _lastUpdateCheck.value = settings.lastUpdateCheck
                    when {
                        release == null -> if (manual) showMessage("Ya tenés la última versión")
                        else -> {
                            _update.value = UpdateState.Available(release)
                            if (!manual) _updateDialog.value = true
                        }
                    }
                }
                .onFailure { if (manual) showMessage("No se pudo buscar actualizaciones. Revisá la conexión.") }
            _checkingUpdates.value = false
        }
    }

    /**
     * Baja la versión nueva y se la pasa al instalador de Android. Devuelve qué abrir antes: el
     * permiso para instalar apps la primera vez, o el navegador si la descarga falla de entrada.
     */
    fun installUpdate(): android.content.Intent? {
        val state = _update.value as? UpdateState.Available ?: return null
        _updateDialog.value = false
        if (!updater.canInstall()) {
            showMessage("Permití que Stocktin instale apps y volvé a tocar Actualizar")
            return updater.installPermissionIntent()
        }
        val release = state.release
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(release, null)
            runCatching {
                val apk = updater.download(release) { p -> _update.value = UpdateState.Downloading(release, p) }
                _update.value = UpdateState.Installing(release)
                updater.install(apk)
            }.onFailure {
                _update.value = UpdateState.Available(release)
                showMessage("No se pudo descargar la actualización. Revisá la conexión y probá de nuevo.")
            }
        }
        return null
    }

    /** Después de actualizar: borra el APK bajado y prepara las novedades de esta versión. */
    private fun afterUpdate() {
        val installed = runCatching { updater.installedVersionCode }.getOrDefault(0)
        updater.clearDownloads()
        val seen = settings.lastSeenVersion
        if (seen == 0) {
            // Primera vez que se abre: no hay novedades que mostrar.
            settings.lastSeenVersion = installed
        } else if (installed > seen) {
            settings.lastSeenVersion = installed
            viewModelScope.launch { _whatsNew.value = runCatching { updater.release(installed) }.getOrNull() }
        }
    }

    fun dismissWhatsNew() {
        _whatsNew.value = null
    }

    fun loadReleases() {
        if (_releases.value != null) return
        viewModelScope.launch {
            _releases.value = runCatching { updater.releases() }.getOrElse {
                showMessage("No se pudo cargar el historial de versiones")
                null
            }
        }
    }

    fun setThemeMode(mode: String) {
        settings.themeMode = mode
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        settings.dynamicColor = enabled
        _dynamicColor.value = enabled
    }

    fun dismissUpdateDialog() {
        _updateDialog.value = false
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val LIVE_REFRESH_MS = 30_000L
    }
}
