package app.stocktin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import app.stocktin.data.Quote
import app.stocktin.data.STRIP_SYMBOLS
import app.stocktin.data.SortMode
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import app.stocktin.R
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stocktin.data.Format
import app.stocktin.widget.WatchlistWidget

/**
 * Pestaña Listas, al estilo de la app Bolsa de Apple: título grande con la fecha, las listas como
 * chips y cada símbolo con su minigráfico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(viewModel: MainViewModel, selectedSymbol: String?, bottomPadding: Dp) {
    val lists by viewModel.lists.collectAsState()
    val currentId by viewModel.currentList.collectAsState()
    val quotes by viewModel.quotes.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val lastRefresh by viewModel.lastRefresh.collectAsState()
    val error by viewModel.lastError.collectAsState()
    val showValue by viewModel.showValue.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val list = lists.firstOrNull { it.id == currentId } ?: lists.first()
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(viewModel)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current

    // Orden que se ve: el de la lista o el elegido en "Ordenar". Mientras se arrastra una fila, el
    // orden vive acá y se guarda recién al soltarla.
    val sorted = if (editing) list.symbols else sortMode.apply(list.symbols, quotes)
    var dragOrder by remember(list.id) { mutableStateOf<List<String>?>(null) }
    val shown = dragOrder ?: sorted
    val listState = rememberLazyListState()
    // El precio más ancho de la lista marca el ancho de la columna de precios.
    val measurer = rememberTextMeasurer()
    val priceStyle = MaterialTheme.typography.titleMedium.tabular().copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    val priceWidth = remember(list.symbols, quotes, priceStyle) {
        val widest = list.symbols.mapNotNull { quotes[it] }
            .maxOfOrNull { measurer.measure(Format.price(it.price), priceStyle).size.width } ?: 0
        with(density) { widest.toDp() } + 10.dp
    }
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val current = dragOrder ?: sorted
        val fromIndex = current.indexOfFirst { it == from.key }
        val toIndex = current.indexOfFirst { it == to.key }
        // Solo entre símbolos: la tira, los chips y el resumen de arriba no se mueven.
        if (fromIndex >= 0 && toIndex >= 0) {
            dragOrder = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val onDragStarted: () -> Unit = {
        dragOrder = shown
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onDragStopped: () -> Unit = {
        dragOrder?.let { if (it != sorted) viewModel.setOrder(it) }
        dragOrder = null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(R.drawable.ic_logo),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 10.dp).size(32.dp),
                            )
                            Text("Stocktin", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            todayLabel().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scroll,
                actions = {
                    if (editing) {
                        FilledTonalButton(onClick = { editing = false }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Filled.Check, null)
                            Spacer(Modifier.padding(start = 6.dp))
                            Text("Listo")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                            ListMenu(
                                expanded = menu,
                                listName = list.name,
                                canMove = lists.size > 1,
                                onDismiss = { menu = false },
                                onAction = { action ->
                                    menu = false
                                    when (action) {
                                        ListAction.CREATE -> nameDialog = NameDialog.Create
                                        ListAction.RENAME -> nameDialog = NameDialog.Rename(list.id, list.name)
                                        ListAction.EDIT -> editing = true
                                        ListAction.MOVE_LEFT -> viewModel.moveList(list.id, -1)
                                        ListAction.MOVE_RIGHT -> viewModel.moveList(list.id, 1)
                                        ListAction.DELETE -> confirmDelete = true
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding)) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)) {
                item(key = "strip") {
                    MarketStrip(quotes) { viewModel.open(Screen.Detail(it)) }
                }
                item(key = "chips") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lists.forEach { l ->
                            FilterChip(
                                selected = l.id == list.id,
                                onClick = { viewModel.selectList(l.id) },
                                label = { Text(l.name) },
                            )
                        }
                        AssistChip(
                            onClick = { nameDialog = NameDialog.Create },
                            label = { Text("Nueva") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                        )
                    }
                }
                item(key = "summary") {
                    val listQuotes = list.symbols.mapNotNull { quotes[it] }
                    val market = WatchlistWidget.marketLabel(listQuotes)
                    val status = buildString {
                        market?.let { append(it.first).append(" · ") }
                        append(
                            when {
                                refreshing && lastRefresh == 0L -> "Actualizando…"
                                lastRefresh == 0L -> "Deslizá hacia abajo para actualizar"
                                error != null -> "Sin conexión · ${Format.time(lastRefresh)}"
                                else -> "Actualizado ${Format.time(lastRefresh)}"
                            },
                        )
                    }
                    Summary(
                        quotes = listQuotes,
                        status = if (editing) "Arrastrá ≡ para ordenar o tocá el tacho para quitar" else status,
                        sortMode = sortMode,
                        onSort = viewModel::setSortMode,
                        showSort = !editing && list.symbols.size > 1,
                    )
                }
                if (list.symbols.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Esta lista está vacía", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tocá la lupa para buscar acciones, ETFs, índices, monedas o cripto.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(shown, key = { it }) { symbol ->
                    ReorderableItem(reorder, key = symbol) { dragging ->
                        SwipeToRemove(enabled = !editing && !dragging, onRemove = { viewModel.removeSymbol(symbol) }) {
                            QuoteRow(
                                symbol = symbol,
                                quote = quotes[symbol],
                                showValue = showValue,
                                selected = symbol == selectedSymbol,
                                editing = editing,
                                dragging = dragging,
                                onClick = { viewModel.open(Screen.Detail(symbol)) },
                                onToggleValue = { viewModel.setShowValue(!showValue) },
                                onRemove = { viewModel.removeSymbol(symbol) },
                                priceWidth = priceWidth,
                                dragModifier = if (editing) {
                                    Modifier
                                } else {
                                    Modifier.longPressDraggableHandle(onDragStarted = { onDragStarted() }, onDragStopped = { onDragStopped() })
                                },
                                handleModifier = Modifier.draggableHandle(onDragStarted = { onDragStarted() }, onDragStopped = { onDragStopped() }),
                            )
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

private enum class ListAction { CREATE, RENAME, EDIT, MOVE_LEFT, MOVE_RIGHT, DELETE }

@Composable
private fun ListMenu(
    expanded: Boolean,
    listName: String,
    canMove: Boolean,
    onDismiss: () -> Unit,
    onAction: (ListAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Nueva lista") }, onClick = { onAction(ListAction.CREATE) })
        DropdownMenuItem(text = { Text("Renombrar \"$listName\"") }, onClick = { onAction(ListAction.RENAME) })
        DropdownMenuItem(text = { Text("Ordenar y quitar símbolos") }, onClick = { onAction(ListAction.EDIT) })
        if (canMove) {
            DropdownMenuItem(text = { Text("Mover lista a la izquierda") }, onClick = { onAction(ListAction.MOVE_LEFT) })
            DropdownMenuItem(text = { Text("Mover lista a la derecha") }, onClick = { onAction(ListAction.MOVE_RIGHT) })
        }
        DropdownMenuItem(text = { Text("Eliminar \"$listName\"") }, onClick = { onAction(ListAction.DELETE) })
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

/** Tira con los índices principales y Bitcoin, para ver cómo viene el día de un vistazo. */
@Composable
private fun MarketStrip(quotes: Map<String, Quote>, onOpen: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        STRIP_SYMBOLS.forEach { (symbol, name) ->
            val quote = quotes[symbol]
            Surface(
                onClick = { onOpen(symbol) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(132.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (quote == null) {
                        Placeholder(Modifier.padding(top = 6.dp).fillMaxWidth().height(34.dp))
                    } else {
                        Text(
                            Format.price(quote.price),
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Format.percent(quote.changePercent),
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = changeColor(quote.change),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Sparkline(
                                prices = quote.spark,
                                baseline = quote.previousClose,
                                color = changeColor(quote.change),
                                modifier = Modifier.weight(1f).height(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cuántos suben y cuántos bajan en la lista, con una barra proporcional, y el orden elegido. */
@Composable
private fun Summary(quotes: List<Quote>, status: String, sortMode: SortMode, onSort: (SortMode) -> Unit, showSort: Boolean) {
    val up = quotes.count { it.change > 0 }
    val down = quotes.count { it.change < 0 }
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (quotes.isNotEmpty()) {
                    Text(
                        buildString {
                            append("$up suben")
                            append(" · $down bajan")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showSort) {
                Box {
                    TextButton(onClick = { sortMenu = true }) { Text(sortMode.label) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                leadingIcon = { if (mode == sortMode) Icon(Icons.Filled.Check, null) },
                                onClick = {
                                    sortMenu = false
                                    onSort(mode)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (up + down > 0) {
            // Barra de "amplitud": verde lo que sube, rojo lo que baja.
            Row(Modifier.padding(top = 6.dp, end = 12.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                if (up > 0) Box(Modifier.weight(up.toFloat()).fillMaxHeight().background(Up))
                if (up > 0 && down > 0) Spacer(Modifier.width(2.dp))
                if (down > 0) Box(Modifier.weight(down.toFloat()).fillMaxHeight().background(Down))
            }
        }
    }
}
