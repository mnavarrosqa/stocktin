package app.stocktin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.stocktin.alerts.AlertNotifier
import app.stocktin.data.Alert
import app.stocktin.data.Alerts
import app.stocktin.data.Format
import java.util.Locale
import kotlin.math.abs

/** En el detalle: las alertas de este símbolo y el botón para crear una. */
@Composable
fun AlertsBlock(alerts: List<Alert>, price: Double, onNew: () -> Unit, onRemove: (Alert) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alertas de precio", style = MaterialTheme.typography.titleSmall)
                    if (alerts.isEmpty()) {
                        Text(
                            "Te avisamos cuando llegue al precio que marques",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(onClick = onNew) { Text("Nueva") }
            }
            alerts.sortedByDescending { it.target }.forEach { alert ->
                AlertRow(alert, price, showSymbol = false, onRemove = { onRemove(alert) })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Una alerta: la flecha, el precio objetivo, cuánto falta y el botón para borrarla. */
@Composable
fun AlertRow(alert: Alert, price: Double?, showSymbol: Boolean, onRemove: () -> Unit, onClick: (() -> Unit)? = null) {
    val color = if (alert.above) Up else Down
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (alert.above) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (showSymbol) "${alert.symbol} · " else "") +
                    (if (alert.above) "Sube a " else "Baja a ") + Format.price(alert.target),
                style = MaterialTheme.typography.bodyLarge.tabular(),
                fontWeight = FontWeight.Medium,
            )
            if (price != null && price > 0) {
                val left = (alert.target - price) / price * 100
                Text(
                    "Falta ${Format.percent(abs(left)).removePrefix("+")} · ahora ${Format.price(price)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Borrar alerta") }
    }
}

/**
 * La hoja para crear una alerta: el precio (arranca 5 % arriba del de ahora) y atajos de ±5 % y ±10 %.
 * Al crear la primera, Android pide permiso para mostrar notificaciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlertSheet(symbol: String, price: Double, onCreate: (Double) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(plain(price * 1.05)) }
    val target = Alerts.parsePrice(text)
    val valid = target != null && target != price
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Aunque diga que no, la alerta queda creada; en Ajustes se explica cómo activarlas.
        target?.let(onCreate)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Avisarme cuando $symbol…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Ahora está a ${Format.price(price)}",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Precio") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleLarge.tabular(),
                supportingText = {
                    Text(
                        when {
                            target == null -> "Escribí un precio"
                            target == price -> "Elegí un precio distinto al de ahora"
                            else -> Alerts.describe(target, price)
                        },
                        color = when {
                            !valid -> MaterialTheme.colorScheme.onSurfaceVariant
                            target!! > price -> Up
                            else -> Down
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-10, -5, 5, 10).forEach { step ->
                    val value = price * (1 + step / 100.0)
                    FilterChip(
                        selected = target != null && plain(target) == plain(value),
                        onClick = { text = plain(value) },
                        label = { Text(if (step > 0) "+$step %" else "−${-step} %") },
                    )
                }
            }
            Button(
                onClick = {
                    val value = target ?: return@Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertNotifier.canNotify(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onCreate(value)
                        onDismiss()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Crear alerta") }
            Text(
                "Se revisa cada vez que se actualizan los precios (con la app abierta o en segundo plano, según Ajustes). Suena una vez y se borra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** El precio para escribir: sin separador de miles y con los decimales que hagan falta. */
private fun plain(value: Double): String =
    if (value >= 1) String.format(Locale.US, "%.2f", value) else String.format(Locale.US, "%.4f", value)
