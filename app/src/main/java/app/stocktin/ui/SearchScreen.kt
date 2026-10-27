package app.stocktin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import app.stocktin.data.Format
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Sugerencias para cuando todavía no se escribió nada. */
private val SUGGESTIONS = listOf(
    "^GSPC" to "S&P 500",
    "^IXIC" to "Nasdaq",
    "^DJI" to "Dow Jones",
    "^MERV" to "Merval",
    "BTC-USD" to "Bitcoin",
    "ETH-USD" to "Ethereum",
    "GC=F" to "Oro",
    "CL=F" to "Petróleo",
    "ARS=X" to "Dólar/Peso",
    "EURUSD=X" to "Euro/Dólar",
    "MELI" to "Mercado Libre",
    "YPF" to "YPF",
    "GGAL" to "Galicia",
    "TSLA" to "Tesla",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val currentId by viewModel.currentList.collectAsState()
    val list = lists.firstOrNull { it.id == currentId } ?: lists.first()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Símbolo o nombre (AAPL, Bitcoin…)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, "Borrar") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Se agrega a \"${list.name}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (query.isBlank()) {
                SearchHome(viewModel, list.symbols)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.symbol }) { result ->
                        val added = result.symbol in list.symbols
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.rememberSearch(result.symbol)
                                    viewModel.open(Screen.Detail(result.symbol))
                                }
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(result.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(result.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(result.type, result.exchange).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.toggleSymbol(result.symbol) }) {
                                Icon(
                                    if (added) Icons.Default.Check else Icons.Default.Add,
                                    if (added) "Quitar de la lista" else "Agregar a la lista",
                                    tint = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lo que se ve antes de escribir: búsquedas recientes, lo más buscado hoy y algunos populares. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHome(viewModel: MainViewModel, inList: List<String>) {
    val recent by viewModel.recent.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val quotes by viewModel.quotes.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (recent.isNotEmpty()) {
            item(key = "recent-title") {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recientes", Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearRecent) { Text("Borrar") }
                }
            }
            item(key = "recent") {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { symbol ->
                        AssistChip(onClick = { viewModel.open(Screen.Detail(symbol)) }, label = { Text(symbol) })
                    }
                }
            }
        }
        if (trending.isNotEmpty()) {
            item(key = "trending-title") { SectionTitle("Lo más buscado hoy", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
            items(trending, key = { "t-$it" }) { symbol ->
                val quote = quotes[symbol]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.rememberSearch(symbol)
                            viewModel.open(Screen.Detail(symbol))
                        }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            quote?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (quote != null) {
                        Text(
                            Format.percent(quote.changePercent),
                            color = changeColor(quote.change),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge.tabular(),
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSymbol(symbol) }) {
                        val added = symbol in inList
                        Icon(
                            if (added) Icons.Default.Check else Icons.Default.Add,
                            if (added) "Quitar de la lista" else "Agregar a la lista",
                            tint = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item(key = "popular-title") { SectionTitle("Populares", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
        item(key = "popular") {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { (symbol, name) ->
                    val added = symbol in inList
                    AssistChip(
                        onClick = { viewModel.toggleSymbol(symbol) },
                        label = { Text(name) },
                        leadingIcon = { Icon(if (added) Icons.Default.Check else Icons.Default.Add, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = modifier)
}
