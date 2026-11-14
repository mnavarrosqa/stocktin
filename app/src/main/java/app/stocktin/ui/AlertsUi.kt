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
import androidx.compose.ui.draw.clip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import app.stocktin.data.AlertKind
import app.stocktin.data.Alerts
import app.stocktin.data.Quote
import app.stocktin.data.Format
import java.util.Locale
import kotlin.math.abs

/** En el detalle: las alertas de este símbolo y el botón para crear una. */
@Composable
fun AlertsBlock(alerts: List<Alert>, quote: Quote, onNew: () -> Unit, onRemove: (Alert) -> Unit) {
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
                    Text("Alertas", style = MaterialTheme.typography.titleSmall)
                    if (alerts.isEmpty()) {
                        Text(
                            "Te avisamos cuando llegue a un precio o se mueva mucho",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(onClick = onNew) { Text("Nueva") }
            }
            alerts.sortedWith(compareBy<Alert> { it.kind }.thenByDescending { it.target }).forEach { alert ->
                AlertRow(alert, quote, showSymbol = false, onRemove = { onRemove(alert) })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Una alerta: la flecha, el precio objetivo (o el movimiento), cuánto falta y el botón para borrarla. */
@Composable
fun AlertRow(alert: Alert, quote: Quote?, showSymbol: Boolean, onRemove: () -> Unit, onClick: (() -> Unit)? = null) {
    val move = alert.kind == AlertKind.DAY_MOVE
    val signal = alert.kind in setOf(AlertKind.YEAR_HIGH, AlertKind.YEAR_LOW, AlertKind.MA200_CROSS)
    val price = quote?.price
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            move || signal -> Text(
                if (move) "±" else "◆",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
            )
            else -> Icon(
                if (alert.above) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null,
                tint = if (alert.above) Up else Down,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (showSymbol) "${alert.symbol} · " else "") + when {
                    move -> "Se mueve más de ${Format.percent(alert.target).removePrefix("+")} en el día"
                    alert.kind == AlertKind.YEAR_HIGH -> "Toca su máximo de 52 semanas"
                    alert.kind == AlertKind.YEAR_LOW -> "Toca su mínimo de 52 semanas"
                    alert.kind == AlertKind.MA200_CROSS -> if (alert.above) "Cae debajo de su media de 200 días" else "Supera su media de 200 días"
                    alert.above -> "Sube a ${Format.price(alert.target)}"
                    else -> "Baja a ${Format.price(alert.target)}"
                },
                style = MaterialTheme.typography.bodyLarge.tabular(),
                fontWeight = FontWeight.Medium,
            )
            if (signal && quote != null) {
                Text(
                    when (alert.kind) {
                        AlertKind.YEAR_HIGH -> "Máximo ${quote.yearHigh?.let(Format::price) ?: "—"} · ahora ${Format.price(quote.price)}"
                        AlertKind.YEAR_LOW -> "Mínimo ${quote.yearLow?.let(Format::price) ?: "—"} · ahora ${Format.price(quote.price)}"
                        else -> "Media ${quote.avg200?.let(Format::price) ?: "—"} · ahora ${Format.price(quote.price)}"
                    },
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (move && quote != null) {
                Text(
                    "Hoy ${Format.percent(quote.changePercent)} · suena una vez por día",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (price != null && price > 0) {
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
 * La hoja para crear una alerta: de precio (arranca 5 % arriba del de ahora, con atajos de ±5 % y
 * ±10 %) o de movimiento del día (más de 3, 5 o 10 %). Al crear la primera, Android pide permiso para
 * mostrar notificaciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlertSheet(
    symbol: String,
    price: Double,
    onCreate: (Double) -> Unit,
    onCreateMove: (Double) -> Unit,
    onDismiss: () -> Unit,
    onCreateSignal: (AlertKind) -> Unit = {},
) {
    val context = LocalContext.current
    // 0: precio, 1: se mueve mucho, 2: una señal.
    var mode by remember { mutableStateOf(0) }
    val move = mode == 1
    var signal by remember { mutableStateOf(AlertKind.YEAR_HIGH) }
    var text by remember { mutableStateOf(plain(price * 1.05)) }
    var percentText by remember { mutableStateOf("5") }
    val target = Alerts.parsePrice(text)
    val percent = Alerts.parsePrice(percentText)?.takeIf { it < 100 }
    val valid = when (mode) {
        1 -> percent != null
        2 -> true
        else -> target != null && target != price
    }
    val create = {
        when (mode) {
            1 -> percent?.let(onCreateMove)
            2 -> onCreateSignal(signal)
            else -> target?.let(onCreate)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Aunque diga que no, la alerta queda creada; en Ajustes se explica cómo activarlas.
        create()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Avisarme cuando $symbol…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Un precio", "Se mueve", "Una señal").forEachIndexed { i, label ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            if (mode == 2) {
                // Señales que miran muchos inversores: extremos del año y la media de 200 días.
                listOf(
                    AlertKind.YEAR_HIGH to "Toque su máximo de 52 semanas",
                    AlertKind.YEAR_LOW to "Toque su mínimo de 52 semanas",
                    AlertKind.MA200_CROSS to "Cruce su media de 200 días (para arriba o para abajo)",
                ).forEach { (kind, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { signal = kind }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = signal == kind, onClick = { signal = kind })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else if (move) {
                OutlinedTextField(
                    value = percentText,
                    onValueChange = { percentText = it },
                    label = { Text("Más de (%)") },
                    singleLine = true,
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.tabular(),
                    supportingText = {
                        Text(
                            if (percent == null) "Escribí un porcentaje" else "Para arriba o para abajo, en un mismo día. Suena una vez por día.",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10).forEach { step ->
                        FilterChip(
                            selected = percent == step.toDouble(),
                            onClick = { percentText = "$step" },
                            label = { Text("$step %") },
                        )
                    }
                }
            } else {
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
                                target == price -> "Elegí un precio distinto al de ahora (${Format.price(price)})"
                                else -> Alerts.describe(target, price)
                            },
                            color = when {
                                !valid || target == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                target > price -> Up
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
            }
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertNotifier.canNotify(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        create()
                        onDismiss()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Crear alerta") }
            Text(
                "Se revisa cada vez que se actualizan los precios (con la app abierta o en segundo plano, según Ajustes)." +
                    if (mode == 0) " Suena una vez y se borra." else " Suena como mucho una vez por día.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** El precio para escribir: sin separador de miles y con los decimales que hagan falta. */
private fun plain(value: Double): String =
    if (value >= 1) String.format(Locale.US, "%.2f", value) else String.format(Locale.US, "%.4f", value)
