package app.stocktin.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import app.stocktin.alerts.AlertNotifier
import app.stocktin.data.Settings
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
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

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        askNotificationsOnce()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            StocktinTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
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

    /** Una sola vez: permiso para los avisos de la agenda y las alertas (Android 13 en adelante). */
    private fun askNotificationsOnce() {
        val settings = Settings(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || settings.askedNotifications) return
        settings.askedNotifications = true
        if (!AlertNotifier.canNotify(this)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }?.let(viewModel::selectTab)
        intent?.getStringExtra(EXTRA_SYMBOL)?.let(viewModel::openFromWidget)
    }

    /**
     * Baja la versión nueva y abre el "¿Actualizar Stocktin?" de Android. La primera vez lleva al
     * permiso para que Stocktin pueda instalar apps.
     */
    private fun update() {
        val intent = viewModel.installUpdate() ?: return
        runCatching { startActivity(intent) }
    }

    companion object {
        const val EXTRA_SYMBOL = "symbol"
        /** Pestaña para abrir (por ejemplo, Mercados desde un aviso de la agenda). */
        const val EXTRA_TAB = "tab"
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

    // Después de actualizar, una vez: qué trae esta versión.
    val whatsNew by viewModel.whatsNew.collectAsState()
    whatsNew?.let { news ->
        AlertDialog(
            onDismissRequest = viewModel::dismissWhatsNew,
            icon = { Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(48.dp)) },
            title = { Text("Novedades de la ${news.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    news.notes.take(10).forEach { Text("• $it") }
                    if (news.notes.isEmpty()) Text("Mejoras y correcciones.")
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissWhatsNew) { Text("Listo") } },
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
    val update by viewModel.update.collectAsState()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        TabContent(viewModel, tab, selectedSymbol = null, bottomPadding = 96.dp + navBar, onUpdate = onUpdate)
        AnimatedVisibility(
            visible = top == null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            FloatingTabBar(
                current = tab,
                onSelect = viewModel::selectTab,
                onSearch = { viewModel.open(Screen.Search) },
                badge = if (update != null) Tab.SETTINGS else null,
            )
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
 * Pantalla ancha (foldable desplegado o tablet): la lista a la izquierda y el detalle a la derecha.
 * En un foldable en modo libro los paneles se separan justo en la bisagra. Con ancho mediano
 * (un foldable desplegado) las pestañas van en la barra flotante abajo de la lista, para no
 * quitarle lugar; recién en tablets grandes pasan a una barra lateral.
 */
@Composable
private fun WideLayout(viewModel: MainViewModel, tab: Tab, detail: Screen.Detail?, onUpdate: () -> Unit) {
    val update by viewModel.update.collectAsState()
    val fold = rememberFoldingFeature()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val expanded = maxWidth >= 840.dp
        val railWidth = if (expanded) 80.dp else 0.dp
        val hinge = fold?.takeIf { it.isBook }?.hingeDp()
        val content = maxWidth - railWidth
        val listWidth = when {
            tab == Tab.SETTINGS -> content
            hinge != null -> (hinge.first - railWidth).coerceAtMost(content - 280.dp).coerceAtLeast(280.dp)
            expanded -> (content * 0.4f).coerceIn(360.dp, 480.dp)
            else -> content * 0.5f
        }
        val gap = if (hinge != null && tab != Tab.SETTINGS) hinge.second - hinge.first else 0.dp
        val bottomPadding = if (expanded) 0.dp else 96.dp + navBar
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
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
                            icon = { TabIcon(item, contentDescription = null, badge = item == Tab.SETTINGS && update != null) },
                            label = { Text(item.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            Box(Modifier.width(listWidth).fillMaxHeight()) {
                TabContent(viewModel, tab, detail?.symbol, bottomPadding = bottomPadding, onUpdate = onUpdate)
                if (!expanded) {
                    FloatingTabBar(
                        current = tab,
                        onSelect = viewModel::selectTab,
                        onSearch = { viewModel.open(Screen.Search) },
                        badge = if (update != null) Tab.SETTINGS else null,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                    )
                }
            }
            if (tab == Tab.SETTINGS) return@Row
            Spacer(Modifier.width(gap))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
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
