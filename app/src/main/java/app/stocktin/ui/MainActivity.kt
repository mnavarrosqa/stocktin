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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

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
        viewModel.foreground = true
    }

    override fun onStop() {
        super.onStop()
        viewModel.foreground = false
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SYMBOL)?.let(viewModel::openFromWidget)
    }

    /** Android pide permiso una vez para que la app pueda instalar sus actualizaciones. */
    private fun update() {
        if (viewModel.canInstallUpdates()) {
            viewModel.installUpdate()
        } else {
            viewModel.showMessage("Permití instalar apps desde Stocktin y volvé a tocar Actualizar")
            startActivity(viewModel.installPermissionIntent())
        }
    }

    companion object {
        const val EXTRA_SYMBOL = "symbol"
    }
}

@Composable
private fun App(viewModel: MainViewModel, onUpdate: () -> Unit) {
    val stack by viewModel.screen.collectAsState()
    BackHandler(enabled = stack.size > 1) { viewModel.back() }
    when (val screen = stack.last()) {
        Screen.Home -> HomeScreen(viewModel)
        Screen.Search -> SearchScreen(viewModel)
        Screen.Settings -> SettingsScreen(viewModel, onUpdate)
        is Screen.Detail -> DetailScreen(viewModel, screen.symbol)
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
