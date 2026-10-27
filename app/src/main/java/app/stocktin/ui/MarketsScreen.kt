package app.stocktin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stocktin.data.Format
import app.stocktin.data.MARKET_SECTIONS
import app.stocktin.data.Quote

/** Pestaña Mercados: índices, cripto, materias primas y monedas en tarjetas con su gráfico del día. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(viewModel: MainViewModel, selectedSymbol: String?, bottomPadding: Dp) {
    val quotes by viewModel.quotes.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                MARKET_SECTIONS.forEach { section ->
                    item(key = "title-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    items(section.symbols, key = { it.first }) { (symbol, name) ->
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
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(78.dp))
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
