package app.stocktin.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.stocktin.R
import app.stocktin.data.Alerts
import app.stocktin.data.Format
import app.stocktin.data.Holding
import app.stocktin.data.Portfolio
import app.stocktin.data.PortfolioTotal
import app.stocktin.data.Position
import app.stocktin.data.Quote
import java.util.Locale

/** "USD 1,234.56" (sin moneda si Yahoo no la dice). */
fun money(value: Double, currency: String?): String = listOfNotNull(currency, Format.price(value)).joinToString(" ")

/** Saldo oculto (el ojito de la cartera): los montos y las cantidades se tapan, los porcentajes no. */
val LocalHideBalance = compositionLocalOf { false }

private const val HIDDEN = "••••••"

/** [money], o la moneda con puntitos si el saldo está oculto. */
@Composable
private fun shownMoney(value: Double, currency: String?): String =
    if (LocalHideBalance.current) listOfNotNull(currency, HIDDEN).joinToString(" ") else money(value, currency)

/** [shares], o puntitos si el saldo está oculto. */
@Composable
private fun shownShares(value: Double): String = if (LocalHideBalance.current) "••" else shares(value)

/** El ojito para mostrar u ocultar el saldo. */
@Composable
fun HideBalanceButton(onToggle: () -> Unit, tint: Color = LocalContentColor.current) {
    val hidden = LocalHideBalance.current
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
            if (hidden) "Mostrar saldo" else "Ocultar saldo",
            tint = tint,
        )
    }
}

/** "+123.45 (+1.20%)" en verde o rojo (solo "+1.20%" si el saldo está oculto). */
@Composable
private fun GainText(value: Double, percent: Double, style: androidx.compose.ui.text.TextStyle) {
    Text(
        if (LocalHideBalance.current) Format.percent(percent) else "${Format.change(value)} (${Format.percent(percent)})",
        style = style.tabular(),
        color = changeColor(value),
        fontWeight = FontWeight.SemiBold,
    )
}

/** Arriba de las listas: cuánto vale la cartera, cuánto cambió hoy y cuánto ganaste en total. */
@Composable
fun PortfolioCard(totals: List<PortfolioTotal>, onToggleHidden: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_wallet), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tu cartera", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                HideBalanceButton(onToggleHidden)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            if (totals.isEmpty()) {
                Text("Esperando cotizaciones…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            totals.forEach { total ->
                val p = total.position
                Spacer(Modifier.height(6.dp))
                Text(shownMoney(p.value, total.currency), style = MaterialTheme.typography.headlineMedium.tabular(), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Hoy", style = MaterialTheme.typography.labelMedium)
                        GainText(p.dayChange, p.dayPercent, MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Total", style = MaterialTheme.typography.labelMedium)
                        GainText(p.gain, p.gainPercent, MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** La cartera completa: cada posición con su valor y su ganancia; tocar una abre el detalle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioSheet(holdings: Collection<Holding>, quotes: Map<String, Quote>, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val totals = Portfolio.totals(holdings, quotes)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Tu cartera",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            totals.forEach { total ->
                val p = total.position
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(shownMoney(p.value, total.currency), style = MaterialTheme.typography.titleMedium.tabular(), modifier = Modifier.weight(1f))
                    GainText(p.gain, p.gainPercent, MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val rows = holdings.map { h -> Triple(h, quotes[h.symbol], quotes[h.symbol]?.let { Portfolio.position(h, it) }) }
                .sortedByDescending { it.third?.value ?: 0.0 }
            rows.forEach { (h, q, p) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(h.symbol) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${shownShares(h.shares)} × ${Format.price(h.cost)}",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(p?.let { shownMoney(it.value, q?.currency) } ?: "—", style = MaterialTheme.typography.titleSmall.tabular())
                        if (p != null) GainText(p.gain, p.gainPercent, MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "Para cargar o cambiar una posición, abrí el símbolo → Tu posición. Los totales se separan por moneda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** En el detalle: lo que tenés de este símbolo, o el botón para cargarlo. */
@Composable
fun PositionBlock(holding: Holding?, quote: Quote, onEdit: () -> Unit, onToggleHidden: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_wallet), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tu posición", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (holding == null) "Cargá cuántas tenés y ves cuánto ganás" else "${shownShares(holding.shares)} a ${Format.price(holding.cost)} de promedio",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (holding != null) HideBalanceButton(onToggleHidden, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (holding == null) OutlinedButton(onClick = onEdit) { Text("Cargar") } else TextButton(onClick = onEdit) { Text("Editar") }
            }
            if (holding != null) {
                val p = Portfolio.position(holding, quote)
                Row(Modifier.padding(top = 10.dp, end = 8.dp)) {
                    PositionStat("Valor", Modifier.weight(1f)) {
                        Text(shownMoney(p.value, quote.currency), style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold)
                    }
                    PositionStat("Hoy", Modifier.weight(1f)) { GainText(p.dayChange, p.dayPercent, MaterialTheme.typography.bodyMedium) }
                }
                Row(Modifier.padding(top = 8.dp, end = 8.dp)) {
                    PositionStat("Ganancia total", Modifier.weight(1f)) { GainText(p.gain, p.gainPercent, MaterialTheme.typography.bodyMedium) }
                    PositionStat("Costo", Modifier.weight(1f)) {
                        Text(shownMoney(p.costBasis, quote.currency), style = MaterialTheme.typography.bodyMedium.tabular())
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionStat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

/** Cargar o cambiar cuántas unidades tenés y a qué precio promedio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingSheet(
    symbol: String,
    price: Double,
    holding: Holding?,
    onSave: (shares: Double, cost: Double) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sharesText by remember { mutableStateOf(holding?.shares?.let(::shares).orEmpty()) }
    var costText by remember { mutableStateOf(plainPrice(holding?.cost ?: price)) }
    val shares = Alerts.parsePrice(sharesText)
    val cost = Alerts.parsePrice(costText)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Tu posición en $symbol", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = sharesText,
                onValueChange = { sharesText = it },
                label = { Text("Cantidad") },
                placeholder = { Text("Por ejemplo, 10 o 0.5") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleLarge.tabular(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it },
                label = { Text("Precio promedio de compra") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleLarge.tabular(),
                supportingText = {
                    if (shares != null && cost != null) {
                        val p = Position(shares * price, 0.0, shares * cost)
                        Text(
                            "Vale ${Format.price(p.value)} · ${Format.change(p.gain)} (${Format.percent(p.gainPercent)})",
                            color = changeColor(p.gain),
                        )
                    } else {
                        Text("Ahora está a ${Format.price(price)}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (shares != null && cost != null) onSave(shares, cost)
                    onDismiss()
                },
                enabled = shares != null && cost != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Guardar") }
            if (holding != null) {
                TextButton(
                    onClick = {
                        onRemove()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Sacar de la cartera", color = MaterialTheme.colorScheme.error) }
            }
            Text(
                "Queda guardado solo en este teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 10, 0.5, 1.25 (sin ceros de más). */
private fun shares(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')

private fun plainPrice(value: Double): String =
    if (value >= 1) String.format(Locale.US, "%.2f", value) else String.format(Locale.US, "%.4f", value)
