package app.stocktin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stocktin.data.AgendaEvent
import app.stocktin.data.AgendaKind
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
        Box(Modifier.widthIn(min = 76.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = if (past) container.copy(alpha = 0.4f) else container, contentColor = content) {
                Text(
                    event.kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                event.detail,
                "Fecha estimada".takeIf { event.estimated },
                "Ya salió".takeIf { past },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
