package app.stocktin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.stocktin.data.Format
import app.stocktin.data.Holding
import app.stocktin.data.Portfolio
import app.stocktin.data.Quote
import app.stocktin.data.Repository
import app.stocktin.data.Settings
import app.stocktin.ui.MainActivity

/**
 * Widget de la cartera: cuánto vale, cuánto cambió hoy y cuánto ganaste; más alto, también tus
 * posiciones más grandes. Respeta el ojito de Stocktin: con el saldo oculto, solo porcentajes.
 */
class PortfolioWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.get(context)
        val hidden = Settings(context).hideBalance
        provideContent {
            GlanceTheme {
                val quotes by repository.quotes.collectAsState()
                val holdings by repository.holdings.collectAsState()
                Content(holdings.values, quotes, WidgetPalette.of(WidgetTheme.DYNAMIC), hidden)
            }
        }
    }

    @Composable
    fun Content(holdings: Collection<Holding>, quotes: Map<String, Quote>, p: WidgetPalette, hidden: Boolean) {
        val size = LocalSize.current
        val totals = Portfolio.totals(holdings, quotes)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .widgetCorners()
                .background(p.background)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Tu cartera", style = TextStyle(color = p.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            val main = totals.firstOrNull()
            if (main == null) {
                Text(
                    if (holdings.isEmpty()) "Cargá tus posiciones en Stocktin" else "Esperando cotizaciones…",
                    style = TextStyle(color = p.text, fontSize = 13.sp),
                )
                return@Column
            }
            val pos = main.position
            fun money(v: Double) = if (hidden) "••••••" else if (main.currency == "ARS") "$ " + Format.pesos(v) else Format.price(v)
            val big = size.width >= 200.dp
            Text(
                (if (main.currency == "ARS" || hidden) "" else "${main.currency} ") + money(pos.value),
                maxLines = 1,
                style = TextStyle(color = p.text, fontSize = if (big) 26.sp else 20.sp, fontWeight = FontWeight.Bold),
            )
            val dayColor = if (pos.dayChange >= 0) p.up else p.down
            val gainColor = if (pos.gain >= 0) p.up else p.down
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (pos.dayChange >= 0) "▲ " else "▼ ") + Format.percent(pos.dayPercent) + " hoy",
                    maxLines = 1,
                    style = TextStyle(color = dayColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                if (big) {
                    Text(
                        "   Total " + Format.percent(pos.gainPercent),
                        maxLines = 1,
                        style = TextStyle(color = gainColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
            if (!big) {
                Text("Total " + Format.percent(pos.gainPercent), maxLines = 1, style = TextStyle(color = gainColor, fontSize = 12.sp))
            }
            // Más alto: las posiciones más grandes, con su ganancia.
            if (size.height >= 150.dp) {
                Spacer(GlanceModifier.height(8.dp))
                val rows = holdings.mapNotNull { h -> quotes[h.symbol]?.let { q -> h to Portfolio.position(h, q) } }
                    .sortedByDescending { it.second.value }
                    .take(((size.height.value - 110) / 22).toInt().coerceIn(1, 6))
                rows.forEach { (h, position) ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.symbol, maxLines = 1, style = TextStyle(color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                        Text(
                            Format.percent(position.gainPercent),
                            maxLines = 1,
                            style = TextStyle(color = if (position.gain >= 0) p.up else p.down, fontSize = 13.sp),
                        )
                    }
                }
            }
        }
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.schedule(context)
        RefreshScheduler.refreshNow(context)
    }
}
