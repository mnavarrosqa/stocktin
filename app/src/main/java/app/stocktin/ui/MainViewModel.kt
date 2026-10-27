package app.stocktin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stocktin.data.Chart
import app.stocktin.data.ChartRange
import app.stocktin.data.Repository
import app.stocktin.data.SearchResult
import app.stocktin.data.Settings
import app.stocktin.data.YahooApi
import app.stocktin.update.Release
import app.stocktin.update.Updater
import app.stocktin.widget.RefreshScheduler
import app.stocktin.widget.Widgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Detail(val symbol: String) : Screen
}

sealed interface UpdateState {
    val release: Release

    data class Available(override val release: Release) : UpdateState
    data class Downloading(override val release: Release, val progress: Float) : UpdateState
}

data class ChartState(
    val symbol: String,
    val range: ChartRange = ChartRange.DAY,
    val chart: Chart? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = Repository.get(app)
    private val settings = Settings(app)
    private val updater = Updater(app)

    val lists = repository.lists
    val quotes = repository.quotes
    val refreshing = repository.refreshing
    val lastRefresh = repository.lastRefresh
    val lastError = repository.lastError

    private val _currentList = MutableStateFlow(repository.list(settings.currentList)?.id ?: "")
    val currentList: StateFlow<String> = _currentList.asStateFlow()

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
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
    private val _updateDialog = MutableStateFlow(false)
    val updateDialog: StateFlow<Boolean> = _updateDialog.asStateFlow()

    val installedVersion: String = runCatching { updater.installedVersionName }.getOrDefault("")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** La app está a la vista (entre onStart y onStop). */
    @Volatile
    var foreground = false

    private var lastAttempt = 0L

    init {
        RefreshScheduler.schedule(app)
        if (settings.autoCheckUpdates && System.currentTimeMillis() - settings.lastUpdateCheck > UPDATE_CHECK_INTERVAL_MS) {
            checkForUpdates(manual = false)
        }
        // Con la app a la vista, los precios se actualizan solos cada medio minuto.
        viewModelScope.launch {
            while (isActive) {
                if (foreground && System.currentTimeMillis() - lastAttempt > LIVE_REFRESH_MS) refresh(quiet = true)
                delay(5_000)
            }
        }
    }

    fun refresh(quiet: Boolean = false) {
        lastAttempt = System.currentTimeMillis()
        viewModelScope.launch {
            val result = Widgets.refresh(getApplication())
            if (!quiet && result.isFailure) _message.value = "No se pudo actualizar. Revisá la conexión."
        }
    }

    // Navegación

    fun open(screen: Screen) {
        _stack.update { it + screen }
        if (screen is Screen.Detail) loadDetail(screen.symbol)
        if (screen is Screen.Search) {
            _query.value = ""
            _results.value = emptyList()
        }
    }

    /** Vuelve atrás; devuelve false si ya estaba en el inicio. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.size <= 1) return false
        _stack.value = stack.dropLast(1)
        (stack.dropLast(1).last() as? Screen.Detail)?.let { loadDetail(it.symbol) }
        return true
    }

    /** Desde un widget: abre el detalle de [symbol] encima del inicio. */
    fun openFromWidget(symbol: String) {
        _stack.value = listOf(Screen.Home)
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
            repository.removeSymbol(list.id, symbol)
        } else {
            repository.addSymbol(list.id, symbol)
            if (repository.quotes.value[symbol] == null) {
                viewModelScope.launch { repository.refresh(listOf(symbol)) }
            }
        }
        widgetsChanged()
    }

    fun removeSymbol(symbol: String) {
        repository.removeSymbol(_currentList.value, symbol)
        widgetsChanged()
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
        loadChart(symbol, _chart.value?.takeIf { it.symbol == symbol }?.range ?: ChartRange.DAY)
        if (repository.quotes.value[symbol] == null) {
            viewModelScope.launch { repository.refresh(listOf(symbol)) }
        }
    }

    private fun loadChart(symbol: String, range: ChartRange) {
        chartJob?.cancel()
        _chart.value = ChartState(symbol, range, chart = _chart.value?.takeIf { it.symbol == symbol && it.range == range }?.chart)
        chartJob = viewModelScope.launch {
            _chart.value = runCatching { YahooApi.chart(symbol, range) }.fold(
                onSuccess = { ChartState(symbol, range, it, loading = false) },
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

    fun showMessage(text: String) {
        _message.value = text
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
                    if (release != null) {
                        _update.value = UpdateState.Available(release)
                        if (!manual) _updateDialog.value = true
                    } else if (manual) {
                        _message.value = "Ya tenés la última versión"
                    }
                }
                .onFailure { if (manual) _message.value = "No se pudo buscar actualizaciones" }
            _checkingUpdates.value = false
        }
    }

    fun installUpdate() {
        val release = _update.value?.release ?: return
        _updateDialog.value = false
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(release, 0f)
            runCatching {
                val apk = updater.download(release) { progress -> _update.value = UpdateState.Downloading(release, progress) }
                updater.install(apk)
            }.onFailure { _message.value = "No se pudo descargar la actualización" }
            _update.value = UpdateState.Available(release)
        }
    }

    fun canInstallUpdates(): Boolean = updater.canInstall()

    fun installPermissionIntent() = updater.installPermissionIntent()

    fun dismissUpdateDialog() {
        _updateDialog.value = false
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val LIVE_REFRESH_MS = 30_000L
    }
}
