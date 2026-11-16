package app.stocktin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.stocktin.R
import app.stocktin.data.Format
import app.stocktin.data.Holding
import app.stocktin.data.Portfolio
import app.stocktin.data.PortfolioHistory
import app.stocktin.data.PortfolioTotal
import app.stocktin.data.Position
import app.stocktin.data.Quote
import app.stocktin.data.Repository
import app.stocktin.data.Settings
import app.stocktin.ui.MainActivity
import kotlin.math.roundToInt

/**
 * Widget de la cartera, con el mismo diseño que la tarjeta de la app: cuánto vale, cuánto cambió
 * hoy y cuánto ganaste; más grande, la evolución, la barra con el peso de cada símbolo y tus
 * posiciones. Respeta el ojito de Stocktin: con el saldo oculto, solo porcentajes.
 */
class PortfolioWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.get(context)
        val hidden = Settings(context).hideBalance
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val quotes by repository.quotes.collectAsState()
                val holdings by repository.holdings.collectAsState()
                val history by repository.history.collectAsState()
                val lastRefresh by repository.lastRefresh.collectAsState()
                Content(
                    holdings = holdings.values,
                    quotes = quotes,
                    p = WidgetPalette.of(WidgetTheme.from(prefs[WidgetKeys.THEME])),
                    hidden = hidden,
                    history = history,
                    updated = lastRefresh.takeIf { it > 0 }?.let { Format.time(it) },
                )
            }
        }
    }

    @Composable
    fun Content(
        holdings: Collection<Holding>,
        quotes: Map<String, Quote>,
        p: WidgetPalette,
        hidden: Boolean,
        history: Map<String, Map<String, Double>> = emptyMap(),
        updated: String? = null,
    ) {
        val size = LocalSize.current
        val totals = Portfolio.totals(holdings, quotes)
        val padding = if (size.width < 180.dp) 12.dp else 16.dp
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .widgetCorners()
                .background(p.background)
                .padding(horizontal = padding, vertical = if (size.height < 110.dp) 10.dp else 14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = if (size.height < 100.dp) Alignment.CenterVertically else Alignment.Top,
        ) {
            val main = totals.firstOrNull()
            // Bajito (2×1) no entra el título: queda el total y el cambio del día.
            if (size.height >= 100.dp || main == null) Header(p, updated.takeIf { size.width >= 200.dp && main != null })
            if (main == null) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    if (holdings.isEmpty()) "Cargá tus posiciones en Stocktin" else "Esperando cotizaciones…",
                    style = TextStyle(color = p.secondary, fontSize = 13.sp),
                )
                Spacer(GlanceModifier.defaultWeight())
                return@Column
            }
            val tall = size.height >= 150.dp
            val wide = size.width >= 250.dp
            val pos = main.position
            // Ancho y alto: la ganancia total va al lado de la pastilla del día.
            val inline = tall && wide && size.width >= 320.dp
            val other = totals.getOrNull(1)?.takeIf { tall }
            // Glance admite hasta 10 hijos por columna: lo de arriba y lo de abajo van en dos columnas.
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.height(if (tall) 6.dp else 2.dp))
                Total(main, p, hidden, history, tall = tall, wide = wide, width = size.width - padding * 2)
                Spacer(GlanceModifier.height(if (tall) 8.dp else 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayPill(pos, main.currency, p, hidden, showMoney = wide && !inline)
                    if (inline) {
                        Spacer(GlanceModifier.width(10.dp))
                        GainLine(pos, main.currency, p, hidden, showMoney = false)
                    }
                }
                if (size.height >= 110.dp && !inline) {
                    Spacer(GlanceModifier.height(6.dp))
                    GainLine(pos, main.currency, p, hidden, showMoney = size.width >= 200.dp)
                }
                if (other != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Y " + shown(other.position.value, other.currency, hidden) + "  ",
                            maxLines = 1,
                            style = TextStyle(color = p.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            arrow(other.position.dayChange) + Format.percent(other.position.dayPercent) + " hoy",
                            maxLines = 1,
                            style = TextStyle(color = color(other.position.dayChange, p), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
            if (tall) Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                val allocation = Portfolio.allocation(holdings, quotes, main.currency, max = 5)
                // Lo que ocupa lo de arriba, más o menos (Glance no mide): lo que sobra es para las posiciones.
                val used = 28.dp + 18.dp + 6.dp + 38.dp + 8.dp + 24.dp +
                    (if (inline) 0.dp else 22.dp) + (if (other != null) 20.dp else 0.dp) +
                    (if (allocation.size >= 2) 18.dp else 0.dp)
                val left = size.height - used
                if (allocation.size >= 2) {
                    Spacer(GlanceModifier.height(12.dp))
                    AllocationBar(allocation, size.width - padding * 2)
                }
                when {
                    left >= 60.dp -> {
                        Spacer(GlanceModifier.height(6.dp))
                        Positions(holdings, quotes, main.currency, allocation, p, hidden, left - 6.dp)
                    }
                    allocation.size >= 2 && left >= 20.dp -> {
                        Spacer(GlanceModifier.height(6.dp))
                        Legend(allocation, size.width - padding * 2, p)
                    }
                }
            }
        }
    }

    /** Quién es quién en la barra, en una línea: los que entran. */
    @Composable
    private fun Legend(allocation: List<Pair<String, Double>>, width: Dp, p: WidgetPalette) {
        var used = 0f
        val shown = allocation.withIndex().takeWhile { (_, item) ->
            used += item.first.length * 7.5f + 42f
            used <= width.value
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { (i, item) ->
                Row(
                    modifier = GlanceModifier.padding(start = if (i > 0) 10.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = GlanceModifier.size(7.dp).cornerRadius(4.dp).background(SLICES[i % SLICES.size])) {}
                    Spacer(GlanceModifier.width(5.dp))
                    Text(
                        "${item.first} ${(item.second * 100).roundToInt()}%",
                        maxLines = 1,
                        style = TextStyle(color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }

    /** La billetera, "Tu cartera" y la hora de la última actualización. */
    @Composable
    private fun Header(p: WidgetPalette, updated: String?) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_wallet),
                contentDescription = null,
                colorFilter = ColorFilter.tint(p.secondary),
                modifier = GlanceModifier.size(15.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "Tu cartera",
                maxLines = 1,
                style = TextStyle(color = p.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (updated != null) {
                Text(updated, maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 11.sp))
            }
        }
    }

    /** El total grande y, si es ancho y alto, el gráfico de cómo viene la cartera a la derecha. */
    @Composable
    private fun Total(
        main: PortfolioTotal,
        p: WidgetPalette,
        hidden: Boolean,
        history: Map<String, Map<String, Double>>,
        tall: Boolean,
        wide: Boolean,
        width: Dp,
    ) {
        val fontSize = when {
            tall && wide -> 30.sp
            wide -> 26.sp
            else -> 21.sp
        }
        val series = main.currency?.let { PortfolioHistory.series(history, it) }.orEmpty().map { it.second }.takeLast(90)
        val chart = tall && wide && series.size >= 3
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown(main.position.value, main.currency, hidden),
                maxLines = 1,
                style = TextStyle(color = p.text, fontSize = fontSize, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (chart) {
                val up = series.last() >= series.first()
                HistorySpark(series, if (up) WidgetPalette.UP else WidgetPalette.DOWN, (width * 0.3f).coerceAtMost(120.dp), 34.dp)
            }
        }
    }

    /** "▲ +34.542,50 (+2,16%) hoy" en una pastilla verde o roja, como en la app. */
    @Composable
    private fun DayPill(pos: Position, currency: String?, p: WidgetPalette, hidden: Boolean, showMoney: Boolean) {
        val up = pos.dayChange >= 0
        val text = if (hidden || !showMoney) {
            Format.percent(pos.dayPercent)
        } else {
            "${Format.change(pos.dayChange, currency)} (${Format.percent(pos.dayPercent)})"
        }
        Row(
            modifier = GlanceModifier
                .cornerRadius(50.dp)
                .background(if (up) p.upTint else p.downTint)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                arrow(pos.dayChange) + text + " hoy",
                maxLines = 1,
                style = TextStyle(color = if (up) p.up else p.down, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @Composable
    private fun GainLine(pos: Position, currency: String?, p: WidgetPalette, hidden: Boolean, showMoney: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ganancia total  ", maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
            Text(
                if (hidden || !showMoney) {
                    Format.percent(pos.gainPercent)
                } else {
                    "${Format.change(pos.gain, currency)} (${Format.percent(pos.gainPercent)})"
                },
                maxLines = 1,
                style = TextStyle(color = color(pos.gain, p), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    /** La barra con el peso de cada símbolo (Glance no reparte por peso: se calcula el ancho de cada tramo). */
    @Composable
    private fun AllocationBar(allocation: List<Pair<String, Double>>, width: Dp) {
        val gap = 2.dp
        val usable = width - gap * (allocation.size - 1)
        Row(modifier = GlanceModifier.fillMaxWidth().height(6.dp)) {
            allocation.forEachIndexed { i, (_, share) ->
                if (i > 0) Spacer(GlanceModifier.width(gap))
                Box(
                    modifier = GlanceModifier
                        .width((usable * share.toFloat()).coerceAtLeast(3.dp))
                        .height(6.dp)
                        .cornerRadius(3.dp)
                        .background(SLICES[i % SLICES.size]),
                ) {}
            }
        }
    }

    /** Las posiciones más grandes: color de su tramo, símbolo, peso, valor y cómo les va hoy. */
    @Composable
    private fun ColumnScope.Positions(
        holdings: Collection<Holding>,
        quotes: Map<String, Quote>,
        currency: String?,
        allocation: List<Pair<String, Double>>,
        p: WidgetPalette,
        hidden: Boolean,
        space: Dp,
    ) {
        val rows = holdings.mapNotNull { h -> quotes[h.symbol]?.let { q -> Triple(h, q, Portfolio.position(h, q)) } }
            .sortedWith(compareByDescending<Triple<Holding, Quote, Position>> { it.second.currency == currency }.thenByDescending { it.third.value })
            .take((space.value / 24).toInt().coerceIn(1, 6))
        val showValue = LocalSize.current.width >= 250.dp
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            rows.forEach { (holding, quote, position) ->
                val slice = allocation.indexOfFirst { it.first == holding.symbol }
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openSymbol(holding.symbol)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(8.dp)
                            .cornerRadius(4.dp)
                            .background(if (slice >= 0) SLICES[slice % SLICES.size] else p.divider),
                    ) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        holding.symbol,
                        maxLines = 1,
                        style = TextStyle(color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    allocation.getOrNull(slice)?.second?.let { share ->
                        Text("  ${(share * 100).roundToInt()}%", maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    if (showValue) {
                        Text(
                            shown(position.value, quote.currency, hidden) + "   ",
                            maxLines = 1,
                            style = TextStyle(color = p.secondary, fontSize = 12.sp),
                        )
                    }
                    Text(
                        Format.percent(quote.changePercent),
                        maxLines = 1,
                        style = TextStyle(color = color(quote.change, p), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }

    @Composable
    private fun HistorySpark(values: List<Double>, color: Int, width: Dp, height: Dp) {
        val density = LocalContext.current.resources.displayMetrics.density
        val bitmap = remember(values, color, width, height) {
            Sparkline.render(
                prices = values,
                baseline = null,
                widthPx = (width.value * density).toInt(),
                heightPx = (height.value * density).toInt(),
                color = color,
                baselineColor = WidgetPalette.BASELINE,
                density = density,
            )
        }
        if (bitmap != null) {
            Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(width, height))
        }
    }

    private fun arrow(value: Double) = if (value >= 0) "▲ " else "▼ "

    private fun color(value: Double, p: WidgetPalette): ColorProvider = if (value >= 0) p.up else p.down

    /** El monto con su moneda, o puntitos si el saldo está oculto. */
    private fun shown(value: Double, currency: String?, hidden: Boolean): String {
        val symbol = if (currency == "ARS") "$" else currency
        val amount = if (hidden) "••••••" else Format.price(value, currency)
        return listOfNotNull(symbol, amount).joinToString(" ")
    }

    private companion object {
        /** Los mismos colores que la barra de la app. */
        val SLICES = listOf(
            DayNight(Color(0xFF3F6BD8), Color(0xFF9DB8FF)),
            DayNight(Color(0xFFB0559E), Color(0xFFE3B5DA)),
            DayNight(Color(0xFFE0A100), Color(0xFFE0A100)),
            DayNight(Color(0xFF26A69A), Color(0xFF4DB6AC)),
            DayNight(Color(0xFF8A9099), Color(0xFF8A9099)),
        )
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
