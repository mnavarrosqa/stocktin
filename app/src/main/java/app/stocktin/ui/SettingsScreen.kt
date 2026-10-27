package app.stocktin.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import app.stocktin.widget.TickerWidgetReceiver
import app.stocktin.widget.WatchlistWidgetReceiver

private val INTERVALS = listOf(15 to "Cada 15 minutos", 30 to "Cada 30 minutos", 60 to "Cada 1 hora", 0 to "Solo a mano (botón ↻)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onUpdate: () -> Unit, bottomPadding: Dp) {
    val minutes by viewModel.refreshMinutes.collectAsState()
    val saveBattery by viewModel.saveBattery.collectAsState()
    val showValue by viewModel.showValue.collectAsState()
    val autoCheck by viewModel.autoCheckUpdates.collectAsState()
    val checking by viewModel.checkingUpdates.collectAsState()
    val update by viewModel.update.collectAsState()
    val snackbar = rememberMessages(viewModel)
    val context = LocalContext.current

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Ajustes", fontWeight = FontWeight.Bold) }, scrollBehavior = scroll) },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding)) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Section("Widgets")
            Item(
                title = "Agregar widget a la pantalla de inicio",
                summary = "Lista completa o un solo símbolo. También desde el menú de widgets del launcher.",
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(ComponentName(context, WatchlistWidgetReceiver::class.java), null, null)
                    } else {
                        viewModel.showMessage("Mantené presionada la pantalla de inicio → Widgets → Stocktin")
                    }
                },
            )
            Item(
                title = "Agregar widget de un símbolo",
                summary = "Precio grande y gráfico del día",
                onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported) {
                        manager.requestPinAppWidget(ComponentName(context, TickerWidgetReceiver::class.java), null, null)
                    }
                },
            )

            Section("Actualizar cotizaciones en segundo plano")
            INTERVALS.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = minutes == value, onClick = { viewModel.setRefreshMinutes(value) })
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = minutes == value, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
            Text(
                "Android no deja actualizar widgets más seguido que cada 15 minutos en segundo plano. " +
                    "Con la app abierta los precios se actualizan cada 30 segundos, y el botón ↻ del widget actualiza al instante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Toggle(
                title = "Ahorrar batería con la bolsa cerrada",
                summary = "De noche y los fines de semana, una vez por hora (salvo que sigas cripto)",
                checked = saveBattery,
                onChange = viewModel::setSaveBattery,
            )

            Section("Lista")
            Toggle(
                title = "Cambio en dinero",
                summary = "En vez de porcentaje. También se cambia tocando la etiqueta de color.",
                checked = showValue,
                onChange = viewModel::setShowValue,
            )

            Section("Actualizaciones de la app")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        update?.let { "Versión ${it.release.versionName} disponible" } ?: "Buscar actualizaciones",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        when (val u = update) {
                            is UpdateState.Downloading -> "Descargando… ${(u.progress * 100).toInt()} %"
                            is UpdateState.Available -> "Se instala encima, sin perder tus listas"
                            null -> "Versión instalada: ${viewModel.installedVersion}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    checking || update is UpdateState.Downloading -> CircularProgressIndicator(Modifier.size(24.dp))
                    update is UpdateState.Available -> FilledTonalButton(onClick = onUpdate) { Text("Actualizar") }
                    else -> FilledTonalButton(onClick = { viewModel.checkForUpdates(manual = true) }) { Text("Buscar") }
                }
            }
            Toggle(
                title = "Buscar al abrir la app",
                summary = "Como mucho cada 6 horas",
                checked = autoCheck,
                onChange = viewModel::setAutoCheckUpdates,
            )

            Section("Acerca de")
            Text(
                "Stocktin ${viewModel.installedVersion}\n" +
                    "Cotizaciones de Yahoo Finance. Algunas bolsas tienen demora; no es asesoramiento financiero.\n" +
                    "Código y versiones: github.com/mnavarrosqa/stocktin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Item(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
