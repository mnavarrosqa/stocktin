package app.stocktin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stocktin.data.Agenda
import app.stocktin.data.AgendaEvent
import app.stocktin.data.Format
import app.stocktin.data.MARKET_SECTIONS
import app.stocktin.data.MarketHours
import app.stocktin.data.MarketSection
import app.stocktin.data.Movers
import app.stocktin.data.Quote
import app.stocktin.data.SectionStyle
import app.stocktin.widget.WatchlistWidget
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pestaña Mercados: cómo está Wall Street y cuánto falta para que abra o cierre, lo que más se
 * mueve hoy, y las secciones (índices, Argentina, sectores, cripto, materias primas y monedas).
 * Los chips de arriba llevan directo a cada sección.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(viewModel: MainViewModel, selectedSymbol: String?, bottomPadding: Dp) {
    val quotes by viewModel.quotes.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val movers by viewModel.movers.collectAsState()
    val moversKind by viewModel.moversKind.collectAsState()
    val now by viewModel.now.collectAsState()
    val agenda by viewModel.agenda.collectAsState()
    var agendaSheet by remember { mutableStateOf(false) }
    val upcoming = remember(agenda, now / 60_000) { Agenda.upcoming(agenda, now, days = 30) }
    val openEvent: (AgendaEvent) -> Unit = { event ->
        agendaSheet = false
        event.symbol?.let { viewModel.open(Screen.Detail(it)) }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Dónde empieza cada sección en la grilla, para que los chips salten hasta ahí.
    val headerItems = 4 // estado, agenda, chips y destacados
    val sectionStart = buildList {
        var index = headerItems
        MARKET_SECTIONS.forEach { section ->
            add(index)
            index += 1 + if (section.style == SectionStyle.HEATMAP) 1 else section.symbols.size
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Mercados", fontWeight = FontWeight.Bold) }, scrollBehavior = scroll) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshMarkets() },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                    MarketStatus(now, quotes["^GSPC"])
                }
                item(key = "agenda", span = { GridItemSpan(maxLineSpan) }) {
                    // Lo más cercano; el resto del mes, en "Ver todo".
                    AgendaCard(upcoming, now, openEvent) { agendaSheet = true }
                }
                item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        items(MARKET_SECTIONS.indices.toList()) { i ->
                            AssistChip(
                                onClick = { scope.launch { grid.animateScrollToItem(sectionStart[i]) } },
                                label = { Text(MARKET_SECTIONS[i].title) },
                            )
                        }
                    }
                }
                item(key = "movers", span = { GridItemSpan(maxLineSpan) }) {
                    MoversCard(
                        kind = moversKind,
                        quotes = movers[moversKind],
                        selectedSymbol = selectedSymbol,
                        onKind = viewModel::setMoversKind,
                        onOpen = { viewModel.open(Screen.Detail(it)) },
                    )
                }
                MARKET_SECTIONS.forEach { section ->
                    item(key = "title-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(section, quotes)
                    }
                    if (section.style == SectionStyle.HEATMAP) {
                        item(key = "heat-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                            Heatmap(section, quotes, selectedSymbol) { viewModel.open(Screen.Detail(it)) }
                        }
                    } else {
                        items(section.symbols, key = { "${section.title}-${it.first}" }) { (symbol, name) ->
                            MarketCard(
                                name = name,
                                symbol = symbol,
                                quote = quotes[symbol],
                                selected = symbol == selectedSymbol,
                                onClick = { viewModel.open(Screen.Detail(symbol)) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (agendaSheet) {
        AgendaSheet(upcoming, now, openEvent) { agendaSheet = false }
    }
}

/** Si Wall Street está abierto y cuánto falta para que cierre o abra, en la hora del teléfono. */
@Composable
private fun MarketStatus(nowMillis: Long, sp500: Quote?) {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val status = MarketHours.status(now)
    // Un día hábil en que Yahoo dice "cerrado" en horario de rueda es feriado.
    val holiday = status.phase == MarketHours.Phase.OPEN && sp500?.marketState == "CLOSED"
    val (title, color) = when {
        holiday -> "Wall Street cerrado por feriado" to MaterialTheme.colorScheme.onSurfaceVariant
        status.phase == MarketHours.Phase.OPEN -> "Wall Street abierto" to Up
        status.phase == MarketHours.Phase.PRE -> "Pre-apertura en Wall Street" to Color(0xFFE0A100)
        status.phase == MarketHours.Phase.POST -> "Post-cierre en Wall Street" to Color(0xFFE0A100)
        else -> "Wall Street cerrado" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val local = status.next.withZoneSameInstant(zone)
    val spanish = Locale.forLanguageTag("es-AR")
    val time = DateTimeFormatter.ofPattern("H:mm", spanish).format(local)
    val day = when (local.toLocalDate()) {
        now.toLocalDate() -> "a las $time"
        now.toLocalDate().plusDays(1) -> "mañana a las $time"
        else -> "el " + DateTimeFormatter.ofPattern("EEEE", spanish).format(local) + " a las $time"
    }
    val countdown = MarketHours.countdown(now, local)
    val subtitle = when {
        holiday -> "Vuelve a abrir el próximo día hábil"
        status.opensNext -> "Abre en $countdown, $day"
        else -> "Cierra en $countdown, $day"
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Las que más suben, bajan o se operan hoy en EE.UU. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoversCard(
    kind: Movers,
    quotes: List<Quote>?,
    selectedSymbol: String?,
    onKind: (Movers) -> Unit,
    onOpen: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                "Lo que más se mueve hoy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Movers.entries.forEachIndexed { i, k ->
                    SegmentedButton(
                        selected = k == kind,
                        onClick = { onKind(k) },
                        shape = SegmentedButtonDefaults.itemShape(i, Movers.entries.size),
                    ) { Text(k.label, maxLines = 1) }
                }
            }
            when {
                quotes == null -> repeat(4) {
                    Placeholder(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(36.dp))
                }
                quotes.isEmpty() -> Text(
                    "Sin datos por ahora.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                else -> quotes.take(6).forEachIndexed { i, q ->
                    MoverRow(i + 1, q, kind, q.symbol == selectedSymbol) { onOpen(q.symbol) }
                }
            }
        }
    }
}

@Composable
private fun MoverRow(rank: Int, quote: Quote, kind: Movers, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(quote.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                quote.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.price(quote.price), style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.SemiBold)
            Text(
                // En "Más operadas" importa el volumen; en las otras, el cambio.
                if (kind == Movers.ACTIVE) {
                    "Vol. ${quote.volume?.let { Format.compact(it.toDouble()) } ?: "—"} · ${Format.percent(quote.changePercent)}"
                } else {
                    Format.percent(quote.changePercent)
                },
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = if (kind == Movers.ACTIVE) MaterialTheme.colorScheme.onSurfaceVariant else changeColor(quote.change),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Título de una sección con el promedio de lo que se movieron sus símbolos. */
@Composable
private fun SectionTitle(section: MarketSection, quotes: Map<String, Quote>) {
    val changes = section.symbols.mapNotNull { quotes[it.first]?.changePercent }
    Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (changes.isNotEmpty()) {
            val avg = changes.average()
            Text(
                "promedio ${Format.percent(avg)}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = changeColor(avg),
            )
        }
    }
}

/** Sectores como mapa de calor: más verde o más rojo cuanto más se movió cada uno. */
@Composable
private fun Heatmap(section: MarketSection, quotes: Map<String, Quote>, selectedSymbol: String?, onOpen: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
        val columns = (maxWidth / 112.dp).toInt().coerceIn(3, 6)
        // Primero los que más se movieron, para que el mapa se lea de un vistazo.
        val ordered = section.symbols.sortedByDescending { quotes[it.first]?.changePercent ?: Double.NEGATIVE_INFINITY }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ordered.chunked(columns).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    line.forEach { (symbol, name) ->
                        val quote = quotes[symbol]
                        Surface(
                            onClick = { onOpen(symbol) },
                            shape = RoundedCornerShape(14.dp),
                            color = WatchlistWidget.heatColor(quote?.changePercent),
                            contentColor = Color.White,
                            border = if (symbol == selectedSymbol) {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f).height(72.dp),
                        ) {
                            Column(
                                Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    quote?.let { Format.percent(it.changePercent) } ?: "—",
                                    style = MaterialTheme.typography.titleMedium.tabular(),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(symbol, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MarketCard(name: String, symbol: String, quote: Quote?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (quote == null) {
                Placeholder(Modifier.fillMaxWidth().height(78.dp))
                return@Column
            }
            Sparkline(
                prices = quote.spark,
                baseline = quote.previousClose,
                color = changeColor(quote.change),
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.price(quote.price),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    Format.percent(quote.changePercent),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = changeColor(quote.change),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
