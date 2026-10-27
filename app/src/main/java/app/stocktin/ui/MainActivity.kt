package app.stocktin.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stocktin.R

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            StocktinTheme {
                App(viewModel, onUpdate = ::update)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setForeground(false)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SYMBOL)?.let(viewModel::openFromWidget)
    }

    /** Abre la descarga de la versión nueva; al terminar, se toca la notificación para instalarla. */
    private fun update() {
        val intent = viewModel.downloadUpdateIntent() ?: return
        runCatching { startActivity(intent) }
            .onSuccess { viewModel.showMessage("Cuando termine la descarga, tocala para instalar") }
            .onFailure { viewModel.showMessage("No hay un navegador para descargar la actualización") }
    }

    companion object {
        const val EXTRA_SYMBOL = "symbol"
    }
}

@Composable
private fun App(viewModel: MainViewModel, onUpdate: () -> Unit) {
    val tab by viewModel.tab.collectAsState()
    val stack by viewModel.screen.collectAsState()
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.LISTS) { viewModel.back() }
    val detail = stack.filterIsInstance<Screen.Detail>().lastOrNull()
    val searching = stack.lastOrNull() == Screen.Search

    if (isWideScreen()) {
        WideLayout(viewModel, tab, detail, onUpdate)
    } else {
        PhoneLayout(viewModel, tab, stack.lastOrNull(), onUpdate)
    }
    // La búsqueda ocupa toda la pantalla en cualquier tamaño.
    if (isWideScreen()) {
        AnimatedVisibility(
            visible = searching,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) { SearchScreen(viewModel) }
    }
    val showDialog by viewModel.updateDialog.collectAsState()
    val update by viewModel.update.collectAsState()
    val release = update?.release
    if (showDialog && release != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateDialog,
            title = { Text("Versión ${release.versionName} disponible") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    release.notes.take(8).forEach { Text("• $it") }
                    if (release.notes.isEmpty()) Text("Se instala encima, sin perder tus listas.")
                }
            },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdateDialog) { Text("Ahora no") } },
        )
    }
}

@Composable
private fun TabContent(viewModel: MainViewModel, tab: Tab, selectedSymbol: String?, bottomPadding: Dp, onUpdate: () -> Unit) {
    AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { current ->
        when (current) {
            Tab.LISTS -> ListsScreen(viewModel, selectedSymbol, bottomPadding)
            Tab.MARKETS -> MarketsScreen(viewModel, selectedSymbol, bottomPadding)
            Tab.SETTINGS -> SettingsScreen(viewModel, onUpdate, bottomPadding)
        }
    }
}

/** Teléfono: la pestaña de fondo, la barra flotante abajo y el detalle o la búsqueda encima. */
@Composable
private fun PhoneLayout(viewModel: MainViewModel, tab: Tab, top: Screen?, onUpdate: () -> Unit) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        TabContent(viewModel, tab, selectedSymbol = null, bottomPadding = 96.dp + navBar, onUpdate = onUpdate)
        AnimatedVisibility(
            visible = top == null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            FloatingTabBar(current = tab, onSelect = viewModel::selectTab, onSearch = { viewModel.open(Screen.Search) })
        }
        // Mantener la última pantalla mientras se anima la salida.
        var shown by remember { mutableStateOf(top) }
        if (top != null) shown = top
        AnimatedVisibility(
            visible = top != null,
            enter = slideInHorizontally { it / 3 } + fadeIn(),
            exit = slideOutHorizontally { it / 3 } + fadeOut(),
        ) {
            when (val screen = shown) {
                Screen.Search -> SearchScreen(viewModel)
                is Screen.Detail -> DetailScreen(viewModel, screen.symbol)
                null -> Unit
            }
        }
    }
}

/**
 * Pantalla ancha (foldable desplegado o tablet): barra lateral, la lista a la izquierda y el
 * detalle a la derecha. En un foldable en modo libro, los paneles se separan justo en la bisagra.
 */
@Composable
private fun WideLayout(viewModel: MainViewModel, tab: Tab, detail: Screen.Detail?, onUpdate: () -> Unit) {
    val fold = rememberFoldingFeature()
    val railWidth = 80.dp
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxHeight().width(railWidth),
            header = {
                FloatingActionButton(onClick = { viewModel.open(Screen.Search) }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.Search, "Buscar")
                }
            },
        ) {
            Spacer(Modifier.weight(1f))
            Tab.entries.forEach { item ->
                NavigationRailItem(
                    selected = tab == item,
                    onClick = { viewModel.selectTab(item) },
                    icon = { TabIcon(item, contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
            Spacer(Modifier.weight(1f))
        }
        if (tab == Tab.SETTINGS) {
            Box(Modifier.weight(1f)) { SettingsScreen(viewModel, onUpdate, bottomPadding = 0.dp) }
            return@Row
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val hinge = fold?.takeIf { it.isBook }?.hingeDp()
            val listWidth = if (hinge != null) {
                (hinge.first - railWidth).coerceAtMost(maxWidth - 240.dp).coerceAtLeast(240.dp)
            } else {
                (maxWidth * 0.42f).coerceIn(320.dp, 480.dp)
            }
            val gap = if (hinge != null) hinge.second - hinge.first else 0.dp
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    TabContent(viewModel, tab, detail?.symbol, bottomPadding = 0.dp, onUpdate = onUpdate)
                }
                Spacer(Modifier.width(gap))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    AnimatedContent(targetState = detail?.symbol, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pane") { symbol ->
                        if (symbol != null) {
                            DetailScreen(viewModel, symbol, inPane = true)
                        } else {
                            EmptyPane()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPane() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_tab_markets),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Elegí un símbolo", style = MaterialTheme.typography.titleMedium)
        Text(
            "Su gráfico y sus datos aparecen acá.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
