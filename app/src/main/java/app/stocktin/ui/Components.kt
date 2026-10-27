package app.stocktin.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stocktin.R
import app.stocktin.data.Format
import app.stocktin.data.Quote

/** Números con todas las cifras del mismo ancho, para que los precios no "bailen" al cambiar. */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

@Composable
fun TabIcon(tab: Tab, contentDescription: String?) = when (tab) {
    Tab.LISTS -> Icon(Icons.AutoMirrored.Filled.List, contentDescription)
    Tab.MARKETS -> Icon(painterResource(R.drawable.ic_tab_markets), contentDescription)
    Tab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription)
}

/**
 * Pestañas en una píldora que flota sobre el contenido, con el botón de búsqueda al lado (como
 * en las apps actuales de Google y Apple): la lista llega hasta el borde y la barra no le quita lugar.
 */
@Composable
fun FloatingTabBar(current: Tab, onSelect: (Tab) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Tab.entries.forEach { item ->
                    TabPill(item, selected = item == current, onClick = { onSelect(item) })
                }
            }
        }
        Surface(
            onClick = onSearch,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 10.dp,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, contentDescription = "Buscar", modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Una pestaña: solo el ícono, o el ícono con su nombre cuando está elegida. */
@Composable
private fun TabPill(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tabBackground",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 18.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            TabIcon(tab, contentDescription = if (selected) null else tab.label)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/** Fila de una lista: símbolo y nombre, minigráfico, precio y cambio. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuoteRow(
    symbol: String,
    quote: Quote?,
    showValue: Boolean,
    selected: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleValue: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "rowBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .combinedClickable(enabled = !editing, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Filled.KeyboardArrowUp, "Subir") }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Filled.KeyboardArrowDown, "Bajar") }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Quitar") }
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
            modifier = Modifier.width(60.dp).height(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.price(quote.price),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(10.dp))
                ChangePill(quote, showValue, onToggleValue)
            }
            val ext = quote.extChangePercent
            if (quote.extSession != null && ext != null) {
                Row(Modifier.padding(top = 3.dp)) {
                    Text(
                        "${quote.extSession.label} ${quote.extPrice?.let(Format::price).orEmpty()}  ",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Format.percent(ext),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = changeColor(ext),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Etiqueta con el cambio del día. Tocarla alterna entre porcentaje y dinero. */
@Composable
fun ChangePill(quote: Quote, showValue: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = changeColor(quote.change),
        contentColor = Color.White,
        modifier = Modifier.widthIn(min = 78.dp),
    ) {
        Box(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                if (showValue) Format.change(quote.change) else Format.percent(quote.changePercent),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge.tabular(),
            )
        }
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

/** Fecha de hoy para los títulos ("martes 27 de octubre"). */
fun todayLabel(): String =
    java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale.forLanguageTag("es-AR"))
        .format(java.time.LocalDate.now())
