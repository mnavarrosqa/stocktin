package app.stocktin.ui

import androidx.compose.foundation.horizontalScroll
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
    val list = lists.firstOrNull { it.id == currentId } ?: lists.first()
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = rememberMessages(viewModel)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Stocktin", fontWeight = FontWeight.Bold)
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
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)) {
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
                item(key = "status") {
                    val market = WatchlistWidget.marketLabel(list.symbols.mapNotNull { quotes[it] })
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
                            if (editing) append(" · Ordená o quitá símbolos")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                items(list.symbols, key = { it }) { symbol ->
                    QuoteRow(
                        symbol = symbol,
                        quote = quotes[symbol],
                        showValue = showValue,
                        selected = symbol == selectedSymbol,
                        editing = editing,
                        onClick = { viewModel.open(Screen.Detail(symbol)) },
                        onLongClick = { editing = true },
                        onToggleValue = { viewModel.setShowValue(!showValue) },
                        onMove = { viewModel.moveSymbol(symbol, it) },
                        onRemove = { viewModel.removeSymbol(symbol) },
                    )
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
