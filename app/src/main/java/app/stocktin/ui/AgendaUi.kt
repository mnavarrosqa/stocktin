package app.stocktin.ui

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stocktin.data.AgendaEvent
import app.stocktin.data.AgendaInfo
import app.stocktin.data.AgendaKind
import app.stocktin.data.Format
import app.stocktin.data.NewsItem
import app.stocktin.data.Quote
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-AR")

/** "Hoy", "Mañana" o "Jueves 6 de noviembre". */
fun agendaDay(time: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish).format(day).replaceFirstChar { it.uppercase() }
    }
}

/** El color de la etiqueta de cada clase de evento. */
@Composable
private fun kindColor(kind: AgendaKind): Pair<Color, Color> = when (kind) {
    AgendaKind.FED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    AgendaKind.EARNINGS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

/** En Mercados: lo próximo de la agenda, con un botón para ver todo el mes. */
@Composable
fun AgendaCard(events: List<AgendaEvent>, now: Long, onOpen: (AgendaEvent) -> Unit, onMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agenda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "La Fed, datos de EE.UU. y balances de tus acciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (events.size > SHOWN) TextButton(onClick = onMore) { Text("Ver todo") }
            }
            if (events.isEmpty()) {
                Text(
                    "Nada importante en las próximas semanas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            AgendaList(events.take(SHOWN), now, onOpen)
        }
    }
}

private const val SHOWN = 5

/** Los eventos agrupados por día. */
@Composable
private fun AgendaList(events: List<AgendaEvent>, now: Long, onOpen: (AgendaEvent) -> Unit) {
    events.groupBy { agendaDay(it.time, now) }.forEach { (day, items) ->
        Text(
            day,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
        )
        items.forEach { AgendaRow(it, now, onOpen) }
    }
}

@Composable
private fun AgendaRow(event: AgendaEvent, now: Long, onOpen: (AgendaEvent) -> Unit) {
    val past = event.time < now && !event.estimated
    val (container, content) = kindColor(event.kind)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(event) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (event.estimated) "—" else DateTimeFormatter.ofPattern("H:mm", spanish).format(Instant.ofEpochMilli(event.time).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = if (past) container.copy(alpha = 0.4f) else container, contentColor = content) {
                    Text(
                        event.kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                val sub = listOfNotNull(
                    event.detail,
                    "Fecha estimada".takeIf { event.estimated },
                    "Ya salió".takeIf { past },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Toda la agenda de las próximas semanas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaSheet(events: List<AgendaEvent>, now: Long, onOpen: (AgendaEvent) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Agenda",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            AgendaList(events, now, onOpen)
            Text(
                "Horarios en tu hora. Las fechas de la Fed y de los datos de EE.UU. son las oficiales; las de los " +
                    "balances las informa Yahoo Finance y pueden cambiar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Lo que se ve al tocar un evento: cuándo es y cuánto falta, qué es y por qué mueve al mercado, lo
 * que conviene mirar cuando sale, las últimas noticias sobre el tema y un botón para agendarlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    event: AgendaEvent,
    now: Long,
    quotes: Map<String, Quote>,
    news: List<NewsItem>?,
    onOpenSymbol: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val (container, content) = kindColor(event.kind)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
                Text(
                    event.kind.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val time = DateTimeFormatter.ofPattern("H:mm", spanish).format(Instant.ofEpochMilli(event.time).atZone(ZoneId.systemDefault()))
            Text(
                agendaDay(event.time, now) + (if (event.estimated) " · hora sin confirmar" else " a las $time (tu hora)") +
                    " · " + countdown(event.time, now),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(AgendaInfo.explain(event), style = MaterialTheme.typography.bodyLarge)

            Text("Qué mirar cuando sale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AgendaInfo.watch(event).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (symbol, name) ->
                        WatchTile(name, quotes[symbol], Modifier.weight(1f)) { onOpenSymbol(symbol) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.time)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.time + 30 * 60 * 1000L)
                        .putExtra(CalendarContract.Events.TITLE, event.title)
                        .putExtra(CalendarContract.Events.DESCRIPTION, AgendaInfo.explain(event))
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar a mi calendario")
            }

            Text("Noticias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                news == null -> repeat(3) { Placeholder(Modifier.fillMaxWidth().height(64.dp).padding(vertical = 4.dp)) }
                news.isEmpty() -> Text(
                    "No encontramos noticias sobre esto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> news.take(6).forEach { NewsItemRow(it) }
            }
        }
    }
}

/** Una cajita con el nombre, el precio y el cambio de hoy; tocarla abre el símbolo. */
@Composable
private fun WatchTile(name: String, quote: Quote?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (quote == null) {
                Text("—", style = MaterialTheme.typography.titleMedium)
            } else {
                Text(Format.price(quote.price), style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold)
                Text(
                    Format.percent(quote.changePercent),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = changeColor(quote.changePercent),
                )
            }
        }
    }
}

/** "en 2 días", "en 3 h", "en 20 min" o "ya salió". */
private fun countdown(time: Long, now: Long): String {
    val minutes = (time - now) / 60_000
    return when {
        minutes < 0 -> "ya salió"
        minutes < 60 -> "en $minutes min"
        minutes < 24 * 60 -> "en ${minutes / 60} h ${minutes % 60} min"
        else -> {
            val days = minutes / (24 * 60)
            if (days == 1L) "en 1 día" else "en $days días"
        }
    }
}
