package app.stocktin.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.stocktin.data.ChartRange
import app.stocktin.data.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: MainViewModel, symbol: String) {
    val quotes by viewModel.quotes.collectAsState()
    val chartState by viewModel.chart.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val quote = quotes[symbol]
    val state = chartState?.takeIf { it.symbol == symbol }
    var selected by remember(symbol, state?.range) { mutableStateOf<Int?>(null) }
    var listMenu by remember { mutableStateOf(false) }
    val inAnyList = lists.any { symbol in it.symbols }
    val snackbar = rememberMessages(viewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                title = { Text(symbol, fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { listMenu = true }) {
                            Icon(if (inAnyList) Icons.Default.Check else Icons.Default.Add, "Listas")
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (quote == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Text(quote.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(quote.exchange, quote.currency, Format.marketState(quote.marketState).ifEmpty { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // Precio: el del punto que se está mirando en el gráfico, o el actual.
            val chart = state?.chart
            val prices = chart?.prices.orEmpty()
            val range = state?.range ?: ChartRange.DAY
            val baseline = if (range == ChartRange.DAY) quote.previousClose ?: chart?.previousClose else prices.firstOrNull()
            val shownPrice = selected?.let { prices.getOrNull(it) } ?: if (range == ChartRange.DAY) quote.price else prices.lastOrNull() ?: quote.price
            val change = if (range == ChartRange.DAY && selected == null) quote.change else baseline?.let { shownPrice - it } ?: 0.0
            val percent = if (range == ChartRange.DAY && selected == null) quote.changePercent
            else baseline?.takeIf { it != 0.0 }?.let { change / it * 100 } ?: 0.0

            Text(Format.price(shownPrice), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Format.change(change)} (${Format.percent(percent)})",
                    color = changeColor(change),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    selected?.let { chart?.timestamps?.getOrNull(it) }?.let { pointTime(it, range) }
                        ?: if (range == ChartRange.DAY) "hoy" else range.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val ext = quote.extChangePercent
            if (quote.extSession != null && ext != null && selected == null) {
                Text(
                    (if (quote.extSession == app.stocktin.data.ExtSession.PRE) "Pre-apertura: " else "Post-cierre: ") +
                        "${quote.extPrice?.let(Format::price).orEmpty()} (${Format.percent(ext)})",
                    color = changeColor(ext),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                when {
                    prices.size >= 2 -> PriceChart(
                        prices = prices,
                        baseline = baseline,
                        color = changeColor((prices.last() - (baseline ?: prices.first()))),
                        selected = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    state?.loading != false -> CircularProgressIndicator()
                    else -> Text(state?.error ?: "Sin datos para este rango", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChartRange.entries.forEach { r ->
                    FilterChip(selected = r == range, onClick = { viewModel.setRange(r) }, label = { Text(r.label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            val stats = listOfNotNull(
                quote.open?.let { "Apertura" to Format.price(it) },
                quote.previousClose?.let { "Cierre anterior" to Format.price(it) },
                quote.dayHigh?.let { "Máximo del día" to Format.price(it) },
                quote.dayLow?.let { "Mínimo del día" to Format.price(it) },
                if (quote.yearLow != null && quote.yearHigh != null) {
                    "Rango 52 semanas" to "${Format.price(quote.yearLow)} – ${Format.price(quote.yearHigh)}"
                } else null,
                quote.volume?.takeIf { it > 0 }?.let { "Volumen" to Format.compact(it.toDouble()) },
                quote.marketCap?.takeIf { it > 0 }?.let { "Cap. de mercado" to Format.compact(it) },
            )
            stats.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Última cotización: ${Format.time(quote.time * 1000)}. Datos de Yahoo Finance; algunas bolsas tienen demora.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
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
