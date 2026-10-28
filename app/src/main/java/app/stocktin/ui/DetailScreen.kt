package app.stocktin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stocktin.data.ChartCompare
import app.stocktin.data.ChartRange
import app.stocktin.data.ExtSession
import app.stocktin.data.Format
import app.stocktin.data.Quote
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Detalle de un símbolo. En el teléfono ocupa toda la pantalla; en pantallas anchas va en el panel
 * derecho ([inPane]). Medio plegado (modo mesa), el gráfico queda arriba y los datos abajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: MainViewModel, symbol: String, inPane: Boolean = false) {
    val quotes by viewModel.quotes.collectAsState()
    val chartState by viewModel.chart.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val quote = quotes[symbol]
    val state = chartState?.takeIf { it.symbol == symbol }
    var selected by remember(symbol, state?.range) { mutableStateOf<Int?>(null) }
    var listMenu by remember { mutableStateOf(false) }
    var alertSheet by remember(symbol) { mutableStateOf(false) }
    val allAlerts by viewModel.alerts.collectAsState()
    val comparing by viewModel.compare.collectAsState()
    val alerts = allAlerts.filter { it.symbol == symbol }
    val holdings by viewModel.holdings.collectAsState()
    val holding = holdings[symbol]
    var holdingSheet by remember(symbol) { mutableStateOf(false) }
    val inAnyList = lists.any { symbol in it.symbols }
    val snackbar = rememberMessages(viewModel)
    val fold = rememberFoldingFeature()
    val tabletop = fold?.isTabletop == true

    val background = if (inPane) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    // En el panel de la derecha, el panel ya deja lugar para la barra de estado.
    val insets = if (inPane) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets
    Scaffold(
        containerColor = background,
        contentWindowInsets = insets,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                windowInsets = if (inPane) WindowInsets(0) else TopAppBarDefaults.windowInsets,
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) {
                        Icon(if (inPane) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, if (inPane) "Cerrar" else "Volver")
                    }
                },
                title = { Text(symbol, fontWeight = FontWeight.Bold) },
                actions = {
                    if (quote != null) {
                        IconButton(onClick = { alertSheet = true }) {
                            BadgedBox(badge = { if (alerts.isNotEmpty()) Badge { Text("${alerts.size}") } }) {
                                Icon(Icons.Filled.Notifications, "Nueva alerta de precio")
                            }
                        }
                    }
                    Box {
                        FilledTonalButton(onClick = { listMenu = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(if (inAnyList) Icons.Filled.Check else Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (inAnyList) "En tus listas" else "Agregar")
                        }
                        DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                            lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text(list.name) },
                                    leadingIcon = { Checkbox(checked = symbol in list.symbols, onCheckedChange = null) },
                                    onClick = { viewModel.toggleSymbol(symbol, list.id) },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (holdingSheet && quote != null) {
            HoldingSheet(
                symbol = symbol,
                price = quote.price,
                holding = holding,
                onSave = { shares, cost -> viewModel.setHolding(symbol, shares, cost) },
                onRemove = { viewModel.removeHolding(symbol) },
                onDismiss = { holdingSheet = false },
            )
        }
        if (alertSheet && quote != null) {
            NewAlertSheet(
                symbol = symbol,
                price = quote.price,
                onCreate = { viewModel.addAlert(symbol, it) },
                onCreateMove = { viewModel.addMoveAlert(symbol, it) },
                onDismiss = { alertSheet = false },
            )
        }
        if (quote == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val chart = state?.chart
        val prices = chart?.prices.orEmpty()
        val range = state?.range ?: ChartRange.DAY
        val baseline = if (range == ChartRange.DAY) quote.previousClose ?: chart?.previousClose else prices.firstOrNull()
        val benchmark = ChartCompare.benchmarkFor(symbol)
        val start = baseline ?: prices.firstOrNull()
        val overlay = if (comparing && chart != null && start != null) {
            state?.benchmark?.let { other -> ChartCompare.overlay(chart, start, other, if (range == ChartRange.DAY) other.previousClose else null) }
        } else {
            null
        }
        val top: @Composable (Dp) -> Unit = { chartHeight ->
            Header(quote, prices, chart?.timestamps.orEmpty(), range, baseline, selected)
            Spacer(Modifier.height(12.dp))
            ChartBlock(
                prices = prices,
                baseline = baseline,
                loading = state?.loading != false,
                error = state?.error,
                selected = selected,
                onSelect = { selected = it },
                height = chartHeight,
                compare = overlay,
            )
            RangeSelector(range) { viewModel.setRange(it) }
            if (benchmark != null && prices.size >= 2 && start != null) {
                CompareRow(
                    symbol = symbol,
                    benchmarkName = benchmark.second,
                    enabled = comparing,
                    symbolPercent = ChartCompare.percent(prices, start, selected),
                    benchmarkPercent = overlay?.let { ChartCompare.percent(it, start, selected) },
                    onToggle = { viewModel.setCompare(!comparing) },
                )
            }
        }
        if (tabletop && fold != null) {
            // Modo mesa: arriba de la bisagra el gráfico, abajo los datos.
            val (hingeTop, hingeBottom) = fold.hingeDp()
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().height(hingeTop).padding(top = padding.calculateTopPadding())) {
                    top((hingeTop - padding.calculateTopPadding() - 150.dp).coerceAtLeast(80.dp))
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = hingeBottom - hingeTop)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Stats(quote)
                    PositionBlock(holding, quote, onEdit = { holdingSheet = true })
                    AlertsBlock(alerts, quote, onNew = { alertSheet = true }, onRemove = viewModel::removeAlert)
                    News(viewModel, symbol)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                top(if (inPane) 320.dp else 260.dp)
                Stats(quote)
                PositionBlock(holding, quote, onEdit = { holdingSheet = true })
                AlertsBlock(alerts, quote, onNew = { alertSheet = true }, onRemove = viewModel::removeAlert)
                News(viewModel, symbol)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(
    quote: Quote,
    prices: List<Double>,
    timestamps: List<Long>,
    range: ChartRange,
    baseline: Double?,
    selected: Int?,
) {
    // Precio: el del punto que se está mirando en el gráfico, o el actual.
    val shownPrice = selected?.let { prices.getOrNull(it) }
        ?: if (range == ChartRange.DAY) quote.price else prices.lastOrNull() ?: quote.price
    val change = if (range == ChartRange.DAY && selected == null) quote.change else baseline?.let { shownPrice - it } ?: 0.0
    val percent = if (range == ChartRange.DAY && selected == null) {
        quote.changePercent
    } else {
        baseline?.takeIf { it != 0.0 }?.let { change / it * 100 } ?: 0.0
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(quote.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            Format.price(shownPrice),
            style = MaterialTheme.typography.displayMedium.tabular(),
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${Format.change(change)} (${Format.percent(percent)})",
                color = changeColor(change),
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                selected?.let { timestamps.getOrNull(it) }?.let { pointTime(it, range) }
                    ?: if (range == ChartRange.DAY) "Hoy" else range.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val ext = quote.extChangePercent
        if (quote.extSession != null && ext != null && selected == null) {
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    (if (quote.extSession == ExtSession.PRE) "Pre-apertura " else "Post-cierre ") +
                        quote.extPrice?.let(Format::price).orEmpty() + "  ",
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Format.percent(ext), color = changeColor(ext), style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Medium)
            }
        }
        Text(
            listOfNotNull(quote.exchange, quote.currency, Format.marketState(quote.marketState).ifEmpty { null }).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChartBlock(
    prices: List<Double>,
    baseline: Double?,
    loading: Boolean,
    error: String?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    height: Dp,
    compare: List<Double>? = null,
) {
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        when {
            prices.size >= 2 -> PriceChart(
                prices = prices,
                baseline = baseline,
                color = changeColor(prices.last() - (baseline ?: prices.first())),
                selected = selected,
                onSelect = { index ->
                    // Un toquecito al pasar por cada punto, como en Robinhood.
                    if (index != null && index != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(index)
                },
                modifier = Modifier.fillMaxSize(),
                compare = compare,
            )
            loading -> CircularProgressIndicator()
            else -> Text(error ?: "Sin datos para este rango", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** El botón para comparar con el índice y, si está prendido, cuánto rindió cada uno en el rango. */
@Composable
private fun CompareRow(
    symbol: String,
    benchmarkName: String,
    enabled: Boolean,
    symbolPercent: Double?,
    benchmarkPercent: Double?,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChip(
            selected = enabled,
            onClick = onToggle,
            label = { Text("vs $benchmarkName") },
            leadingIcon = if (enabled) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
        )
        if (enabled && benchmarkPercent != null && symbolPercent != null) {
            Column {
                Text(
                    "$symbol ${Format.percent(symbolPercent)}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = changeColor(symbolPercent),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$benchmarkName ${Format.percent(benchmarkPercent)}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (enabled) {
            Text("Cargando…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rangos del gráfico como texto, el elegido resaltado (estilo Robinhood / Bolsa de Apple). */
@Composable
private fun RangeSelector(current: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChartRange.entries.forEach { r ->
            val selected = r == current
            Surface(
                onClick = { onSelect(r) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Stats(quote: Quote) {
    val stats = listOfNotNull(
        quote.open?.let { "Apertura" to Format.price(it) },
        quote.previousClose?.let { "Cierre anterior" to Format.price(it) },
        quote.dayHigh?.let { "Máximo del día" to Format.price(it) },
        quote.dayLow?.let { "Mínimo del día" to Format.price(it) },
        quote.volume?.takeIf { it > 0 }?.let { "Volumen" to Format.compact(it.toDouble()) },
        quote.marketCap?.takeIf { it > 0 }?.let { "Cap. de mercado" to Format.compact(it) },
    )
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
        // En paneles anchos (tablet) entran cuatro datos por fila.
        val columns = if (maxWidth >= 560.dp) 4 else 2
        Column {
            if (quote.yearLow != null && quote.yearHigh != null && quote.yearHigh > quote.yearLow) {
                YearRange(quote.price, quote.yearLow, quote.yearHigh)
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    stats.chunked(columns).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            pair.forEach { (label, value) ->
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Text(
                "Última cotización: ${Format.time(quote.time * 1000)}. Datos de Yahoo Finance; algunas bolsas tienen demora.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Dónde está el precio dentro del rango de 52 semanas. */
@Composable
private fun YearRange(price: Double, low: Double, high: Double) {
    val position = ((price - low) / (high - low)).toFloat().coerceIn(0f, 1f)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Rango de 52 semanas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {}
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(position.coerceAtLeast(0.02f)).height(6.dp),
                ) {}
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(Format.price(low), style = MaterialTheme.typography.bodyMedium.tabular(), modifier = Modifier.weight(1f))
                Text(Format.price(high), style = MaterialTheme.typography.bodyMedium.tabular())
            }
        }
    }
}

private fun pointTime(epochSeconds: Long, range: ChartRange): String {
    val pattern = when (range) {
        ChartRange.DAY -> "H:mm"
        ChartRange.WEEK -> "EEE d H:mm"
        else -> "d MMM yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-AR"))
        .format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))
}

/** Las últimas noticias del símbolo; tocar una la abre en el navegador. */
@Composable
private fun News(viewModel: MainViewModel, symbol: String) {
    val news by viewModel.news.collectAsState()
    val items = news?.takeIf { it.first == symbol }?.second
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Noticias",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        when {
            items == null -> repeat(3) {
                Placeholder(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 4.dp))
            }
            items.isEmpty() -> Text(
                "No hay noticias recientes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
            else -> items.forEach { item ->
                Surface(
                    onClick = { runCatching { uri.openUri(item.link) } },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(item.publisher, timeAgo(item.time)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (item.thumbnail != null) {
                            Spacer(Modifier.width(12.dp))
                            AsyncImage(
                                model = item.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "hace 5 min", "hace 3 h", "hace 2 d". */
private fun timeAgo(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val minutes = (System.currentTimeMillis() / 1000 - epochSeconds) / 60
    return when {
        minutes < 1 -> "recién"
        minutes < 60 -> "hace $minutes min"
        minutes < 60 * 24 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / (60 * 24)} d"
    }
}
