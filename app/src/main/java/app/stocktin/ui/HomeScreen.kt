package app.stocktin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stocktin.data.Format
import app.stocktin.data.Quote
import app.stocktin.widget.WatchlistWidget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val lists by viewModel.lists.collectAsState()
    val currentId by viewModel.currentList.collectAsState()
    val quotes by viewModel.quotes.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val lastRefresh by viewModel.lastRefresh.collectAsState()
    val error by viewModel.lastError.collectAsState()
    val showValue by viewModel.showValue.collectAsState()
    val list = lists.firstOrNull { it.id == currentId } ?: lists.first()
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(viewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stocktin", fontWeight = FontWeight.Bold) },
                actions = {
                    if (editing) {
                        IconButton(onClick = { editing = false }) { Icon(Icons.Default.Check, "Listo") }
                    } else {
                        IconButton(onClick = { viewModel.open(Screen.Search) }) { Icon(Icons.Default.Search, "Buscar") }
                        IconButton(onClick = { viewModel.open(Screen.Settings) }) { Icon(Icons.Default.Settings, "Ajustes") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Nueva lista") }, onClick = {
                                    menu = false
                                    nameDialog = NameDialog.Create
                                })
                                DropdownMenuItem(text = { Text("Renombrar \"${list.name}\"") }, onClick = {
                                    menu = false
                                    nameDialog = NameDialog.Rename(list.id, list.name)
                                })
                                DropdownMenuItem(text = { Text("Ordenar y quitar símbolos") }, onClick = {
                                    menu = false
                                    editing = true
                                })
                                if (lists.size > 1) {
                                    DropdownMenuItem(text = { Text("Mover lista a la izquierda") }, onClick = {
                                        menu = false
                                        viewModel.moveList(list.id, -1)
                                    })
                                    DropdownMenuItem(text = { Text("Mover lista a la derecha") }, onClick = {
                                        menu = false
                                        viewModel.moveList(list.id, 1)
                                    })
                                }
                                DropdownMenuItem(text = { Text("Eliminar \"${list.name}\"") }, onClick = {
                                    menu = false
                                    confirmDelete = true
                                })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!editing) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.open(Screen.Search) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Agregar") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(
                selectedTabIndex = lists.indexOf(list).coerceAtLeast(0),
                edgePadding = 12.dp,
                divider = {},
            ) {
                lists.forEach { l ->
                    Tab(selected = l.id == list.id, onClick = { viewModel.selectList(l.id) }, text = { Text(l.name) })
                }
            }
            val listQuotes = list.symbols.mapNotNull { quotes[it] }
            val market = WatchlistWidget.marketLabel(listQuotes)
            Text(
                text = buildString {
                    market?.let { append(it.first).append(" · ") }
                    append(
                        when {
                            refreshing && lastRefresh == 0L -> "Actualizando…"
                            lastRefresh == 0L -> "Deslizá hacia abajo para actualizar"
                            error != null -> "Sin conexión · ${Format.time(lastRefresh)}"
                            else -> "Actualizado ${Format.time(lastRefresh)}"
                        },
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (list.symbols.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Esta lista está vacía", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tocá Agregar para buscar acciones, ETFs, índices, monedas o cripto.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(list.symbols, key = { it }) { symbol ->
                            QuoteRow(
                                symbol = symbol,
                                quote = quotes[symbol],
                                showValue = showValue,
                                editing = editing,
                                onClick = { viewModel.open(Screen.Detail(symbol)) },
                                onToggleValue = { viewModel.setShowValue(!showValue) },
                                onMove = { viewModel.moveSymbol(symbol, it) },
                                onRemove = { viewModel.removeSymbol(symbol) },
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    nameDialog?.let { dialog ->
        NameInputDialog(
            title = if (dialog is NameDialog.Rename) "Renombrar lista" else "Nueva lista",
            initial = (dialog as? NameDialog.Rename)?.name.orEmpty(),
            onDismiss = { nameDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    NameDialog.Create -> viewModel.createList(name)
                    is NameDialog.Rename -> viewModel.renameList(dialog.id, name)
                }
                nameDialog = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar \"${list.name}\"?") },
            text = { Text("Los widgets que la muestran pasan a mostrar otra lista.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteList(list.id)
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private sealed interface NameDialog {
    data object Create : NameDialog
    data class Rename(val id: String, val name: String) : NameDialog
}

@Composable
private fun NameInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nombre") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun QuoteRow(
    symbol: String,
    quote: Quote?,
    showValue: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
    onToggleValue: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !editing, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                quote?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editing) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Default.KeyboardArrowDown, "Bajar") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Quitar") }
            return@Row
        }
        if (quote == null) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Row
        }
        Sparkline(
            prices = quote.spark,
            baseline = quote.previousClose,
            color = changeColor(quote.change),
            modifier = Modifier.width(64.dp).height(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.price(quote.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                ChangePill(quote, showValue, onToggleValue)
            }
            val ext = quote.extChangePercent
            if (quote.extSession != null && ext != null) {
                Row(Modifier.padding(top = 2.dp)) {
                    Text(
                        "${quote.extSession.label} ${quote.extPrice?.let(Format::price).orEmpty()}  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Format.percent(ext), style = MaterialTheme.typography.bodySmall, color = changeColor(ext), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Etiqueta con el cambio del día. Tocarla alterna entre porcentaje y dinero. */
@Composable
fun ChangePill(quote: Quote, showValue: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(changeColor(quote.change))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            if (showValue) Format.change(quote.change) else Format.percent(quote.changePercent),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Muestra los mensajes de la app como snackbar. */
@Composable
fun rememberMessages(viewModel: MainViewModel): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            host.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    return host
}
