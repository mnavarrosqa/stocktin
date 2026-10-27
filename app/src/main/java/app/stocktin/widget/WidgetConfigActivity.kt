package app.stocktin.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.stocktin.data.Repository
import app.stocktin.ui.StocktinTheme
import kotlinx.coroutines.launch

/** Opciones de un widget: se abre al ponerlo y al mantenerlo presionado → Configurar. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Si se sale sin guardar, el widget no se agrega.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val ticker = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            ?.provider?.className == TickerWidgetReceiver::class.java.name

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            setContent {
                StocktinTheme {
                    ConfigScreen(ticker, prefs) { save ->
                        lifecycleScope.launch {
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { save(it) }
                            Widgets.updateAll(this@WidgetConfigActivity)
                            RefreshScheduler.schedule(this@WidgetConfigActivity)
                            RefreshScheduler.refreshNow(this@WidgetConfigActivity)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    private fun ConfigScreen(
        ticker: Boolean,
        initial: Preferences,
        onSave: ((androidx.datastore.preferences.core.MutablePreferences) -> Unit) -> Unit,
    ) {
        val repository = remember { Repository.get(this) }
        val lists = repository.lists.value
        var listId by remember { mutableStateOf(initial[WidgetKeys.LIST_ID] ?: lists.firstOrNull()?.id) }
        var symbol by remember { mutableStateOf(initial[WidgetKeys.SYMBOL] ?: repository.allSymbols().firstOrNull() ?: "SPY") }
        var theme by remember { mutableStateOf(WidgetTheme.from(initial[WidgetKeys.THEME])) }
        var spark by remember { mutableStateOf(initial[WidgetKeys.SHOW_SPARK] ?: true) }
        var name by remember { mutableStateOf(initial[WidgetKeys.SHOW_NAME] ?: true) }
        var pill by remember { mutableStateOf(initial[WidgetKeys.PILL] ?: false) }
        var ext by remember { mutableStateOf(initial[WidgetKeys.SHOW_EXT] ?: true) }
        var value by remember { mutableStateOf(initial[WidgetKeys.SHOW_VALUE] ?: false) }

        Scaffold(topBar = { TopAppBar(title = { Text(if (ticker) "Widget de un símbolo" else "Widget de lista") }) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ticker) {
                    Section("Símbolo")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repository.allSymbols().forEach { s ->
                            FilterChip(selected = s == symbol, onClick = { symbol = s }, label = { Text(s) })
                        }
                    }
                } else {
                    Section("Lista")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lists.forEach { list ->
                            FilterChip(selected = list.id == listId, onClick = { listId = list.id }, label = { Text(list.name) })
                        }
                    }
                }
                Section("Aspecto")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTheme.entries.forEach { t ->
                        FilterChip(selected = t == theme, onClick = { theme = t }, label = { Text(t.label) })
                    }
                }
                if (!ticker) {
                    Toggle("Minigráfico del día", "Se ve si el widget es bastante ancho", spark) { spark = it }
                    Toggle("Nombre de cada símbolo", null, name) { name = it }
                    Toggle("Pre-apertura y post-cierre", "El precio fuera de horario, debajo del cambio", ext) { ext = it }
                }
                Toggle("Cambio en etiqueta de color", "Como en la app Bolsa del iPhone", pill) { pill = it }
                Toggle("Cambio en dinero", "En vez de porcentaje", value) { value = it }
                Button(
                    onClick = {
                        onSave { prefs ->
                            listId?.let { prefs[WidgetKeys.LIST_ID] = it }
                            prefs[WidgetKeys.SYMBOL] = symbol
                            prefs[WidgetKeys.THEME] = theme.name
                            prefs[WidgetKeys.SHOW_SPARK] = spark
                            prefs[WidgetKeys.SHOW_NAME] = name
                            prefs[WidgetKeys.PILL] = pill
                            prefs[WidgetKeys.SHOW_EXT] = ext
                            prefs[WidgetKeys.SHOW_VALUE] = value
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Listo") }
            }
        }
    }

    @Composable
    private fun Section(title: String) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }

    @Composable
    private fun Toggle(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
