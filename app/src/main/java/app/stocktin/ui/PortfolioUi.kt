package app.stocktin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.roundToInt
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

/** Los colores del reparto de la cartera, en orden. */
@Composable
private fun allocationColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    Color(0xFFE0A100),
    Color(0xFF26A69A),
    MaterialTheme.colorScheme.outline,
)

/** "▲ +56.77 (+1.33%) hoy" en una pastilla verde o roja. */
@Composable
private fun GainPill(value: Double, percent: Double, suffix: String) {
    val color = changeColor(value)
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (value >= 0) "▲" else "▼", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            Text(
                (if (LocalHideBalance.current) Format.percent(percent) else "${Format.change(value)} (${Format.percent(percent)})") + " $suffix",
                style = MaterialTheme.typography.labelLarge.tabular(),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** La barra con el peso de cada símbolo y, abajo, quién es quién. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllocationBar(allocation: List<Pair<String, Double>>) {
    if (allocation.size < 2) return
    val colors = allocationColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            allocation.forEachIndexed { i, (_, share) ->
                Box(Modifier.weight(share.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(colors[i % colors.size]))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            allocation.forEachIndexed { i, (symbol, share) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Spacer(Modifier.width(5.dp))
                    Text("$symbol ${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium.tabular())
                }
            }
        }
    }
}

/**
 * Arriba de las listas: cuánto vale la cartera, cuánto cambió hoy, cuánto ganaste en total y cómo
 * se reparte. Con dólares y pesos, la moneda con más valor va grande y las demás en una línea.
 */
@Composable
fun PortfolioCard(holdings: Collection<Holding>, quotes: Map<String, Quote>, onToggleHidden: () -> Unit, onClick: () -> Unit) {
    val totals = Portfolio.totals(holdings, quotes)
    val background = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)),
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.background(background).padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_wallet), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tu cartera", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                HideBalanceButton(onToggleHidden)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
            val main = totals.firstOrNull()
            if (main == null) {
                Text("Esperando cotizaciones…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                return@Column
            }
            val p = main.position
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(shownMoney(p.value, main.currency), style = MaterialTheme.typography.headlineLarge.tabular(), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GainPill(p.dayChange, p.dayPercent, "hoy")
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ganancia total", style = MaterialTheme.typography.labelSmall)
                        GainText(p.gain, p.gainPercent, MaterialTheme.typography.labelLarge)
                    }
                }
                AllocationBar(Portfolio.allocation(holdings, quotes, main.currency))
                totals.drop(1).forEach { other ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shownMoney(other.position.value, other.currency), style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "hoy ${Format.percent(other.position.dayPercent)}",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = changeColor(other.position.dayChange),
                        )
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

/**
 * En el detalle: lo que tenés de este símbolo. Arriba cuánto vale y cuánto ganaste; después dónde
 * quedó tu precio promedio en el rango del año, y los números en cajitas. Sin posición, el botón
 * para cargarla.
 */
@Composable
fun PositionBlock(holding: Holding?, quote: Quote, weight: Double?, onEdit: () -> Unit, onToggleHidden: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_wallet), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tu posición", style = MaterialTheme.typography.titleSmall)
                    if (holding == null) {
                        Text(
                            "Cargá cuántas tenés y ves cuánto ganás",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (holding != null) HideBalanceButton(onToggleHidden, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (holding == null) OutlinedButton(onClick = onEdit) { Text("Cargar") } else TextButton(onClick = onEdit) { Text("Editar") }
            }
            if (holding == null) return@Column
            val p = Portfolio.position(holding, quote)
            Column(Modifier.padding(end = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(shownMoney(p.value, quote.currency), style = MaterialTheme.typography.headlineMedium.tabular(), fontWeight = FontWeight.Bold)
                GainPill(p.gain, p.gainPercent, "desde que compraste")
                if (quote.yearLow != null && quote.yearHigh != null && quote.yearHigh > quote.yearLow) {
                    CostInRange(holding.cost, quote.price, quote.yearLow, quote.yearHigh)
                }
                val tiles = listOfNotNull(
                    Tile("Cantidad") { TileValue(shownShares(holding.shares)) },
                    Tile("Precio promedio") { TileValue(Format.price(holding.cost)) },
                    Tile("Hoy") { GainText(p.dayChange, p.dayPercent, MaterialTheme.typography.titleMedium) },
                    Tile("Costo total") { TileValue(shownMoney(p.costBasis, quote.currency)) },
                    weight?.let { w -> Tile("De tu cartera") { TileValue("${(w * 100).roundToInt()}%") } },
                )
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile -> StatTile(tile.label, Modifier.weight(1f), tile.content) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Una cajita de "Tu posición": el nombre y lo que va adentro. */
private class Tile(val label: String, val content: @Composable () -> Unit)

@Composable
private fun TileValue(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
private fun StatTile(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            value()
        }
    }
}

/**
 * El rango de 52 semanas con dos marcas: tu precio promedio (un rombo) y el precio de ahora (un
 * punto). El tramo entre los dos es verde si vas ganando y rojo si vas perdiendo.
 */
@Composable
private fun CostInRange(cost: Double, price: Double, low: Double, high: Double) {
    val min = minOf(low, cost, price)
    val max = maxOf(high, cost, price)
    fun at(v: Double) = ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val gain = changeColor(price - cost)
    val costColor = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tu promedio y el precio de ahora, en el rango del año", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = size.height / 2
            val r = 3.dp.toPx()
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            val a = size.width * at(cost)
            val b = size.width * at(price)
            drawLine(gain, Offset(minOf(a, b), y), Offset(maxOf(a, b), y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            // Rombo: el precio promedio.
            val d = 6.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(a, y - d); lineTo(a + d, y); lineTo(a, y + d); lineTo(a - d, y); close()
                },
                costColor,
            )
            drawCircle(gain, radius = 7.dp.toPx(), center = Offset(b, y))
            drawCircle(Color.White, radius = r, center = Offset(b, y))
        }
        Row {
            Text("◆ Promedio ${Format.price(cost)}", style = MaterialTheme.typography.labelMedium.tabular(), modifier = Modifier.weight(1f))
            Text("● Ahora ${Format.price(price)}", style = MaterialTheme.typography.labelMedium.tabular(), color = gain)
        }
        Row {
            Text(Format.price(low), style = MaterialTheme.typography.labelSmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(Format.price(high), style = MaterialTheme.typography.labelSmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
