package app.stocktin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stocktin.data.Format
import app.stocktin.data.Quote
import app.stocktin.data.SuggestedList
import app.stocktin.data.Suggestions

/** Abajo de la lista: ideas de listas para armar, en tarjetas que se deslizan de costado. */
@Composable
fun SuggestionsRow(suggestions: List<SuggestedList>, quotes: Map<String, Quote>, onOpen: (SuggestedList) -> Unit) {
    if (suggestions.isEmpty()) return
    Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
        Text(
            "Ideas para armar listas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "Tocá una para ver qué trae y agregarla",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(suggestions, key = { it.id }) { list -> SuggestionCard(list, quotes) { onOpen(list) } }
        }
    }
}

@Composable
private fun SuggestionCard(list: SuggestedList, quotes: Map<String, Quote>, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(200.dp).height(150.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(list.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                list.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                list.symbols.take(4).joinToString(" · ") { it.removeSuffix(".BA").removeSuffix("-USD").removeSuffix("=F").removeSuffix("=X") } +
                    if (list.symbols.size > 4) " +${list.symbols.size - 4}" else "",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                val average = Suggestions.averageChange(list, quotes)
                if (average != null) {
                    Text(
                        "Hoy ${Format.percent(average)}",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = changeColor(average),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("${list.symbols.size} símbolos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Add, "Agregar", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Qué trae una lista sugerida, con las cotizaciones de hoy, y el botón para armarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionSheet(list: SuggestedList, quotes: Map<String, Quote>, onAdd: () -> Unit, onOpenSymbol: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(list.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(list.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Suggestions.averageChange(list, quotes)?.let { average ->
                    Text(
                        "En promedio hoy ${Format.percent(average)}",
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = changeColor(average),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(52.dp),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar a mis listas")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            list.symbols.forEach { symbol ->
                val quote = quotes[symbol]
                Surface(onClick = { onOpenSymbol(symbol) }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                quote?.name?.takeIf { it != symbol } ?: " ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (quote == null) {
                            Placeholder(Modifier.width(80.dp).height(20.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Format.price(quote.price), style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.SemiBold)
                                Text(
                                    Format.percent(quote.changePercent),
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = changeColor(quote.changePercent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
