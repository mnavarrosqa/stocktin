package app.stocktin.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
            modifier = Modifier.size(56.dp),
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
            .padding(horizontal = 16.dp)
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
@Composable
fun QuoteRow(
    symbol: String,
    quote: Quote?,
    showValue: Boolean,
    selected: Boolean,
    editing: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onToggleValue: () -> Unit,
    onRemove: () -> Unit,
    /** Mantener presionada la fila y arrastrar para moverla. */
    /** Ancho del precio más largo de la lista: así los precios quedan alineados sin espacio de más. */
    priceWidth: Dp = 96.dp,
    dragModifier: Modifier = Modifier,
    /** La manija ≡ del modo edición: arrastra sin esperar. */
    handleModifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        when {
            dragging -> MaterialTheme.colorScheme.surfaceContainerHigh
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "rowBackground",
    )
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "rowElevation")
    // En paneles angostos (la mitad de un foldable) se saca el minigráfico para que entre el nombre.
    BoxWithConstraints {
        val compact = maxWidth < 380.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp)
                .shadow(elevation, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .then(dragModifier)
                .clickable(enabled = !editing, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(if (compact) 1f else 1.4f)) {
                Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                if (quote != null) {
                    Text(
                        quote.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Placeholder(Modifier.padding(top = 4.dp).width(110.dp).height(12.dp))
                }
            }
            if (editing) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Quitar", tint = MaterialTheme.colorScheme.error) }
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Arrastrar para mover",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = handleModifier.padding(12.dp),
                )
                return@Row
            }
            if (quote == null) {
                // Mientras llega la primera cotización: la forma de la fila, sin números.
                Placeholder(Modifier.width(60.dp).height(26.dp))
                Spacer(Modifier.width(12.dp))
                Placeholder(Modifier.width(150.dp).height(30.dp))
                return@Row
            }
            if (!compact) {
                // El minigráfico se estira hasta el precio.
                Spacer(Modifier.width(8.dp))
                Sparkline(
                    prices = quote.spark,
                    baseline = quote.previousClose,
                    color = changeColor(quote.change),
                    modifier = Modifier.weight(1f).widthIn(max = 180.dp).height(32.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlashingPrice(symbol, quote.price, priceWidth)
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
}

/**
 * Una fila que se desliza hacia la izquierda para quitarla de la lista (como en Mail o en la app
 * Bolsa de Apple). Debajo aparece el fondo rojo con el tacho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRemove()
            // La fila vuelve a su lugar: si se quitó, desaparece igual porque ya no está en la lista.
            false
        },
        positionalThreshold = { distance -> distance * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, "Quitar", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

/** El precio, con un destello verde o rojo cuando cambia (como en las apps de los brokers). */
@Composable
private fun FlashingPrice(symbol: String, price: Double, width: Dp) {
    val flash = remember(symbol) { Animatable(Color.Transparent) }
    var last by remember(symbol) { mutableStateOf(price) }
    LaunchedEffect(symbol, price) {
        if (price != last) {
            flash.snapTo(changeColor(price - last).copy(alpha = 0.28f))
            last = price
            flash.animateTo(Color.Transparent, tween(durationMillis = 900))
        }
    }
    Text(
        Format.price(price),
        style = MaterialTheme.typography.titleMedium.tabular(),
        fontWeight = FontWeight.SemiBold,
        // Ancho mínimo fijo: así los minigráficos quedan alineados en columna.
        textAlign = TextAlign.End,
        modifier = Modifier
            .widthIn(min = width)
            .clip(RoundedCornerShape(6.dp))
            .background(flash.value)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Un bloque gris del tamaño de lo que va a aparecer. */
@Composable
fun Placeholder(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
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

/** Muestra los mensajes de la app como snackbar, con su botón (por ejemplo, Deshacer). */
@Composable
fun rememberMessages(viewModel: MainViewModel): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val result = host.showSnackbar(current.text, actionLabel = current.actionLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) current.action?.invoke()
        viewModel.messageShown()
    }
    return host
}

/** Fecha de hoy para los títulos ("martes 27 de octubre"). */
fun todayLabel(): String =
    java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale.forLanguageTag("es-AR"))
        .format(java.time.LocalDate.now())
