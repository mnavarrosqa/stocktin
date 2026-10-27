package app.stocktin.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import app.stocktin.R
import app.stocktin.data.Format
import app.stocktin.data.Quote
import app.stocktin.data.Repository
import app.stocktin.ui.MainActivity

/** Widget con una lista de seguimiento: precio, cambio, minigráfico y pre/post de cada símbolo. */
class WatchlistWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.get(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val lists by repository.lists.collectAsState()
            val quotes by repository.quotes.collectAsState()
            val refreshing by repository.refreshing.collectAsState()
            val lastRefresh by repository.lastRefresh.collectAsState()
            val error by repository.lastError.collectAsState()
            val list = lists.firstOrNull { it.id == prefs[WidgetKeys.LIST_ID] } ?: lists.firstOrNull()
            val options = RowOptions(
                palette = WidgetPalette.of(WidgetTheme.from(prefs[WidgetKeys.THEME])),
                showSpark = prefs[WidgetKeys.SHOW_SPARK] ?: true,
                showName = prefs[WidgetKeys.SHOW_NAME] ?: true,
                pill = prefs[WidgetKeys.PILL] ?: false,
                showExt = prefs[WidgetKeys.SHOW_EXT] ?: true,
                showValue = prefs[WidgetKeys.SHOW_VALUE] ?: false,
            )
            val rows = list?.symbols.orEmpty().map { symbol -> symbol to quotes[symbol] }
            val status = when {
                refreshing -> "Actualizando…"
                lastRefresh == 0L -> "Tocá ↻ para actualizar"
                error != null -> "Sin conexión · ${Format.time(lastRefresh)}"
                else -> "Actualizado ${Format.time(lastRefresh)}"
            }
            Content(list?.name ?: "Stocktin", status, marketLabel(rows.mapNotNull { it.second }), rows, options)
        }
    }

    @Composable
    private fun Content(
        title: String,
        status: String,
        market: Pair<String, Boolean>?,
        rows: List<Pair<String, Quote?>>,
        options: RowOptions,
    ) {
        val size = LocalSize.current
        val p = options.palette
        // En widgets angostos se achica todo y se saca lo que no entra.
        val compact = size.width < 230.dp
        val sparkWidth = when {
            !options.showSpark || size.width < 290.dp -> 0.dp
            size.width < 360.dp -> 52.dp
            else -> 68.dp
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(p.background)
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 10.dp),
        ) {
            Header(title, status, market, compact, p)
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Esta lista está vacía.\nTocá para agregar símbolos.",
                        style = TextStyle(color = p.secondary, fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(rows, itemId = { it.first.hashCode().toLong() }) { (symbol, quote) ->
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            QuoteRow(symbol, quote, compact, sparkWidth, options)
                            Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(p.divider))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(title: String, status: String, market: Pair<String, Boolean>?, compact: Boolean, p: WidgetPalette) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(
                    title,
                    maxLines = 1,
                    style = TextStyle(color = p.text, fontSize = if (compact) 15.sp else 17.sp, fontWeight = FontWeight.Bold),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (market != null && !compact) {
                        Text(
                            "● ",
                            style = TextStyle(color = if (market.second) p.up else p.secondary, fontSize = 10.sp),
                        )
                        Text("${market.first} · ", maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                    }
                    Text(status, maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                }
            }
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Actualizar",
                colorFilter = ColorFilter.tint(p.text),
                modifier = GlanceModifier
                    .size(36.dp)
                    .padding(6.dp)
                    .cornerRadius(18.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            )
        }
    }

    @Composable
    private fun QuoteRow(symbol: String, quote: Quote?, compact: Boolean, sparkWidth: Dp, options: RowOptions) {
        val p = options.palette
        val open = actionStartActivity<MainActivity>(
            actionParametersOf(ActionParameters.Key<String>(MainActivity.EXTRA_SYMBOL) to symbol),
        )
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 6.dp else 8.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    symbol,
                    maxLines = 1,
                    style = TextStyle(color = p.text, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold),
                )
                if (options.showName && quote != null) {
                    Text(quote.name, maxLines = 1, style = TextStyle(color = p.secondary, fontSize = if (compact) 11.sp else 13.sp))
                }
            }
            if (sparkWidth > 0.dp && quote != null) {
                SparkImage(quote, sparkWidth, 28.dp)
                Spacer(GlanceModifier.width(10.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                if (quote == null) {
                    Text("—", style = TextStyle(color = p.secondary, fontSize = 15.sp))
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.price(quote.price),
                        maxLines = 1,
                        style = TextStyle(color = p.text, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Change(quote, options, if (compact) 12.sp else 14.sp, if (compact) 62.dp else 76.dp)
                }
                val ext = quote.extChangePercent
                if (options.showExt && quote.extSession != null && ext != null) {
                    Row {
                        Text(
                            "${quote.extSession.label} ${quote.extPrice?.let(Format::price).orEmpty()}  ",
                            maxLines = 1,
                            style = TextStyle(color = p.secondary, fontSize = if (compact) 11.sp else 12.sp),
                        )
                        Text(
                            Format.percent(ext),
                            maxLines = 1,
                            style = TextStyle(
                                color = if (ext >= 0) p.up else p.down,
                                fontSize = if (compact) 11.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }

    private data class RowOptions(
        val palette: WidgetPalette,
        val showSpark: Boolean,
        val showName: Boolean,
        val pill: Boolean,
        val showExt: Boolean,
        val showValue: Boolean,
    )

    @Composable
    private fun Change(quote: Quote, options: RowOptions, fontSize: TextUnit, pillWidth: Dp) =
        ChangeText(quote, options.palette, options.pill, options.showValue, fontSize, pillWidth)

    companion object {
        /** Estado del mercado de la lista (el del primer símbolo que no sea cripto). */
        fun marketLabel(quotes: List<Quote>): Pair<String, Boolean>? {
            val quote = quotes.firstOrNull { it.quoteType != "CRYPTOCURRENCY" && it.marketState != null } ?: return null
            val label = Format.marketState(quote.marketState).takeIf { it.isNotEmpty() } ?: return null
            return label to (quote.marketState == "REGULAR")
        }
    }
}

/** El cambio del día: texto de color o etiqueta de color. */
@Composable
fun ChangeText(quote: Quote, p: WidgetPalette, pill: Boolean, showValue: Boolean, fontSize: TextUnit, pillWidth: Dp) {
    val text = if (showValue) Format.change(quote.change) else Format.percent(quote.changePercent)
    if (pill) {
        Box(
            modifier = GlanceModifier
                .width(pillWidth)
                .cornerRadius(6.dp)
                .background(if (quote.isUp) p.upPill else p.downPill)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text,
                maxLines = 1,
                style = TextStyle(color = p.onPill, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            )
        }
    } else {
        Text(
            text,
            maxLines = 1,
            style = TextStyle(
                color = if (quote.isUp) p.up else p.down,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            ),
            modifier = GlanceModifier.width(pillWidth),
        )
    }
}

/** El minigráfico del día de [quote], con el cierre anterior punteado. */
@Composable
fun SparkImage(quote: Quote, width: Dp, height: Dp) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bitmap = remember(quote.spark, quote.previousClose, quote.isUp, width, height) {
        val w = (width.value * density).toInt()
        Sparkline.render(
            prices = Sparkline.downsample(quote.spark, w / 2),
            baseline = quote.previousClose,
            widthPx = w,
            heightPx = (height.value * density).toInt(),
            color = if (quote.isUp) WidgetPalette.UP else WidgetPalette.DOWN,
            baselineColor = WidgetPalette.BASELINE,
            density = density,
        )
    }
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(width, height),
        )
    } else {
        Spacer(GlanceModifier.size(width, height))
    }
}
