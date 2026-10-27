package app.stocktin.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stocktin.R
import app.stocktin.data.Format
import app.stocktin.update.Release
import app.stocktin.update.Updater
import app.stocktin.widget.TickerWidgetReceiver
import app.stocktin.widget.WatchlistWidgetReceiver
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INTERVALS = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 0 to "Manual")
private val THEMES = listOf("SYSTEM" to "Sistema", "LIGHT" to "Claro", "DARK" to "Oscuro")

/**
 * Ajustes en tarjetas: arriba la versión y las actualizaciones, después los widgets, los datos, la
 * apariencia y "Acerca de".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onUpdate: () -> Unit, bottomPadding: Dp) {
    val minutes by viewModel.refreshMinutes.collectAsState()
    val saveBattery by viewModel.saveBattery.collectAsState()
    val showValue by viewModel.showValue.collectAsState()
    val autoCheck by viewModel.autoCheckUpdates.collectAsState()
    val checking by viewModel.checkingUpdates.collectAsState()
    val update by viewModel.update.collectAsState()
    val lastCheck by viewModel.lastUpdateCheck.collectAsState()
    val lastRefresh by viewModel.lastRefresh.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val snackbar = rememberMessages(viewModel)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uri = LocalUriHandler.current
    var history by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Ajustes", fontWeight = FontWeight.Bold) }, scrollBehavior = scroll) },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding)) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VersionCard(
                installed = viewModel.installedVersion,
                update = update,
                checking = checking,
                lastCheck = lastCheck,
                autoCheck = autoCheck,
                onCheck = { viewModel.checkForUpdates(manual = true) },
                onUpdate = onUpdate,
                onAutoCheck = viewModel::setAutoCheckUpdates,
                onHistory = { history = true },
            )

            Section("Widgets") {
                WidgetsRow()
            }

            Section("Datos") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Actualizar en segundo plano", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        INTERVALS.forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = minutes == value,
                                onClick = { viewModel.setRefreshMinutes(value) },
                                shape = SegmentedButtonDefaults.itemShape(i, INTERVALS.size),
                            ) { Text(label) }
                        }
                    }
                    Text(
                        if (minutes == 0) {
                            "Los widgets se actualizan solo con su botón o al abrir la app."
                        } else {
                            "Android no deja actualizar widgets más seguido que cada 15 minutos. Con la app abierta, cada 30 segundos."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ToggleRow(
                    title = "Ahorrar batería con la bolsa cerrada",
                    summary = "Noches y fines de semana, una vez por hora (salvo que sigas cripto)",
                    checked = saveBattery,
                    onChange = viewModel::setSaveBattery,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cotizaciones", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (lastRefresh == 0L) "Todavía sin datos" else "Actualizadas ${Format.time(lastRefresh)} · Yahoo Finance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        TextButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Ahora")
                        }
                    }
                }
            }

            Section("Apariencia") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tema", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        THEMES.forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = themeMode == value,
                                onClick = { viewModel.setThemeMode(value) },
                                shape = SegmentedButtonDefaults.itemShape(i, THEMES.size),
                            ) { Text(label) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        title = "Colores del fondo de pantalla",
                        summary = if (dynamicColor) "Material You: la app combina con tu teléfono" else "El verde de Stocktin",
                        checked = dynamicColor,
                        onChange = viewModel::setDynamicColor,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ToggleRow(
                    title = "Cambio en dinero",
                    summary = "En vez de porcentaje. También se cambia tocando la etiqueta de color.",
                    checked = showValue,
                    onChange = viewModel::setShowValue,
                )
            }

            Section("Acerca de") {
                LinkRow(Icons.Filled.Star, "Historial de versiones", "Qué trajo cada versión") { history = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LinkRow(Icons.Filled.Info, "Código en GitHub", "github.com/mnavarrosqa/stocktin") {
                    runCatching { uri.openUri(Updater.REPO_PAGE) }
                }
                Text(
                    "Cotizaciones de Yahoo Finance; algunas bolsas tienen demora. Stocktin no es asesoramiento financiero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(bottomPadding + 8.dp))
        }
    }

    if (history) {
        LaunchedEffect(Unit) { viewModel.loadReleases() }
        val releases by viewModel.releases.collectAsState()
        ModalBottomSheet(onDismissRequest = { history = false }) {
            ReleaseHistory(releases, installedCode = Updater.versionCodeOf(viewModel.installedVersion) ?: 0)
        }
    }
}

/** La tarjeta de arriba: qué versión hay, si hay una nueva y el botón para bajarla. */
@Composable
private fun VersionCard(
    installed: String,
    update: UpdateState?,
    checking: Boolean,
    lastCheck: Long,
    autoCheck: Boolean,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onHistory: () -> Unit,
) {
    val highlight = update != null
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(52.dp))
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Stocktin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when (update) {
                            null -> "Versión $installed" + if (lastCheck > 0) " · al día (${Format.time(lastCheck)})" else ""
                            else -> "Versión $installed → ${update.release.versionName}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (update != null) {
                if (update.release.notes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        update.release.notes.take(4).forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                when (update) {
                    is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val p = update.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "Descargando" + (p?.let { " ${(it * 100).toInt()} %" } ?: "…") +
                                (update.release.sizeBytes.takeIf { it > 0 }?.let { " de ${Format.compact(it.toDouble())}B" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is UpdateState.Downloaded -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Lista para instalar. Tocá la notificación de la descarga, o abrí Descargas y tocá el archivo. Se instala encima sin perder tus listas.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) { Text("Abrir descargas") }
                    }
                    is UpdateState.Available -> Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Descargar " + update.release.versionName +
                                (update.release.sizeBytes.takeIf { it > 0 }?.let { " · ${Format.compact(it.toDouble())}B" } ?: ""),
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheck, enabled = !checking, modifier = Modifier.weight(1f)) {
                        if (checking) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Buscar actualizaciones")
                        }
                    }
                    TextButton(onClick = onHistory) { Text("Novedades") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onAutoCheck(!autoCheck) }) {
                Text("Buscar al abrir la app (cada 6 horas)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = autoCheck, onCheckedChange = onAutoCheck)
            }
        }
    }
}

/** Botones para poner cada widget, con su vista previa. */
@Composable
private fun WidgetsRow() {
    val context = LocalContext.current
    fun pin(receiver: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WidgetTile("Lista", "Tus símbolos con gráfico", R.drawable.widget_preview, Modifier.weight(1f)) {
                pin(WatchlistWidgetReceiver::class.java)
            }
            WidgetTile("Un símbolo", "Precio grande, desde 1×1", R.drawable.ticker_preview, Modifier.weight(1f)) {
                pin(TickerWidgetReceiver::class.java)
            }
        }
        Text(
            "Tocá uno para ponerlo en la pantalla de inicio. Para cambiar su aspecto, mantené presionado el widget → Configurar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WidgetTile(title: String, summary: String, preview: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(preview), contentDescription = null, modifier = Modifier.widthIn(max = 96.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Una sección: título y su tarjeta. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ToggleRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Las últimas versiones con sus novedades, la instalada marcada. */
@Composable
private fun ReleaseHistory(releases: List<Release>?, installedCode: Int) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Historial de versiones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (releases == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(releases, key = { it.versionCode }) { release ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(release.versionName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (release.versionCode == installedCode) {
                            Spacer(Modifier.size(8.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    "Instalada",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            releaseDate(release.publishedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    release.notes.ifEmpty { listOf("Mejoras y correcciones") }.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun releaseDate(iso: String?): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM, H:mm", Locale.forLanguageTag("es-AR")).format(OffsetDateTime.parse(iso).toLocalDateTime())
}.getOrDefault("")
