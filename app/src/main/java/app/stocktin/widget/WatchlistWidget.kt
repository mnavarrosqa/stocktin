package app.stocktin.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyVerticalGrid
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
import androidx.glance.unit.ColorProvider
import app.stocktin.R
import app.stocktin.data.Format
import app.stocktin.data.Quote
import app.stocktin.data.Repository
import app.stocktin.ui.MainActivity
import kotlin.math.abs
import kotlin.math.ceil

/** Cómo se muestran los símbolos en el widget de lista. */
enum class WidgetLayout(val label: String) {
    LIST("Lista"),
    MOSAIC("Mosaico");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.name == key } ?: LIST
    }
}

/**
 * Widget con una lista de seguimiento. Se adapta al tamaño: de una columna con lo justo (símbolo
 * y cambio) hasta dos columnas con nombre, minigráfico y pre/post en pantallas anchas. También
 * puede mostrarse como mosaico de colores según cuánto subió o bajó cada símbolo.
 */
class WatchlistWidget(
    /** Falso solo en las capturas: Robolectric no dibuja las listas con scroll de los widgets. */
    private val scrollable: Boolean = true,
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.get(context)
        provideContent {
            GlanceTheme { Widget(repository) }
        }
    }

    @Composable
    private fun Widget(repository: Repository) {
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
            else -> Format.time(lastRefresh)
        }
        Content(
            title = list?.name ?: "Stocktin",
            status = status,
            market = marketLabel(rows.mapNotNull { it.second }),
            rows = rows,
            layout = WidgetLayout.from(prefs[WidgetKeys.LAYOUT]),
            options = options,
        )
    }

    @Composable
    fun Content(
        title: String,
        status: String,
        market: Pair<String, Boolean>?,
        rows: List<Pair<String, Quote?>>,
        layout: WidgetLayout,
        options: RowOptions,
    ) {
        val size = LocalSize.current
        val p = options.palette
        val horizontalPadding = if (size.width < 200.dp) 10.dp else 14.dp
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .widgetCorners()
                .background(p.background)
                .padding(start = horizontalPadding, end = horizontalPadding, top = 10.dp, bottom = 4.dp),
        ) {
            val quotes = rows.mapNotNull { it.second }
            val breadth = quotes.count { it.change > 0 } to quotes.count { it.change < 0 }
            Header(title, status, market, breadth, compact = size.height < 150.dp || size.width < 200.dp, p)
            Spacer(GlanceModifier.height(4.dp))
            when {
                rows.isEmpty() -> Box(
                    modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Esta lista está vacía.\nTocá para agregar símbolos.",
                        style = TextStyle(color = p.secondary, fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                }
                layout == WidgetLayout.MOSAIC -> Mosaic(rows, size.width - horizontalPadding * 2, size.height, options)
                else -> QuoteList(rows, size.width - horizontalPadding * 2, size.height - 66.dp, options)
            }
        }
    }

    @Composable
    private fun Header(
        title: String,
        status: String,
        market: Pair<String, Boolean>?,
        breadth: Pair<Int, Int>,
        compact: Boolean,
        p: WidgetPalette,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                // Una sola línea: título y hora, para dejar lugar a los símbolos.
                Row(
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (market != null) {
                        Text("● ", style = TextStyle(color = if (market.second) p.up else p.secondary, fontSize = 9.sp))
                    }
                    Text(title, maxLines = 1, style = TextStyle(color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text("  $status", maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 11.sp))
                }
            } else {
                Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                    Text(title, maxLines = 1, style = TextStyle(color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (market != null) {
                            Text("● ", style = TextStyle(color = if (market.second) p.up else p.secondary, fontSize = 10.sp))
                            Text("${market.first} · ", maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                        }
                        Text(status, maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                        // Cuántos suben y cuántos bajan, como en la app.
                        if (breadth.first + breadth.second > 0) {
                            Text("   ▲${breadth.first}", maxLines = 1, style = TextStyle(color = p.up, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            Text(" ▼${breadth.second}", maxLines = 1, style = TextStyle(color = p.down, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Actualizar",
                colorFilter = ColorFilter.tint(p.secondary),
                modifier = GlanceModifier
                    .size(if (compact) 30.dp else 36.dp)
                    .padding(6.dp)
                    .cornerRadius(18.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            )
        }
    }

    /**
     * Lista de una columna, o de dos si el widget es muy ancho (tablet o foldable desplegado).
     * Si entran todas las filas, se reparten el alto del widget (sin franja vacía abajo); si no,
     * se hace scroll.
     */
    @Composable
    private fun QuoteList(rows: List<Pair<String, Quote?>>, width: Dp, height: Dp, options: RowOptions) {
        val columns = if (width >= 460.dp) 2 else 1
        val density = RowDensity.of(width / columns - if (columns > 1) 12.dp else 0.dp)
        val lines = ceil(rows.size / columns.toDouble()).toInt()
        val fill = lines <= 10 && lines * density.minHeight <= height
        val last = rows.chunked(columns).lastOrNull().orEmpty().map { it.first }.toSet()
        Grid(rows, columns, fill) { (symbol, quote) ->
            Column(
                modifier = (if (fill) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxWidth())
                    .padding(horizontal = if (columns > 1) 6.dp else 0.dp),
            ) {
                Box(
                    modifier = if (fill) GlanceModifier.fillMaxWidth().defaultWeight() else GlanceModifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    QuoteRow(symbol, quote, density, options)
                }
                if (symbol !in last) {
                    Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(options.palette.divider))
                }
            }
        }
    }

    /** Los símbolos en [columns] columnas: llenando el alto ([fill]) o con scroll si no entran. */
    @Composable
    private fun Grid(
        rows: List<Pair<String, Quote?>>,
        columns: Int,
        fill: Boolean,
        item: @Composable (Pair<String, Quote?>) -> Unit,
    ) {
        when {
            fill || !scrollable -> Column(modifier = GlanceModifier.fillMaxSize()) {
                // Glance admite hasta 10 hijos por fila o columna.
                rows.chunked(columns).take(10).forEach { line ->
                    Row(
                        modifier = if (fill) GlanceModifier.fillMaxWidth().defaultWeight() else GlanceModifier.fillMaxWidth(),
                    ) {
                        line.forEach { Box(modifier = GlanceModifier.defaultWeight()) { item(it) } }
                        repeat(columns - line.size) { Spacer(GlanceModifier.defaultWeight()) }
                    }
                }
            }
            columns == 1 -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.first.hashCode().toLong() }) { item(it) }
            }
            else -> LazyVerticalGrid(gridCells = GridCells.Fixed(columns), modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.first.hashCode().toLong() }) { item(it) }
            }
        }
    }

    /** Cuánto entra en cada fila según su ancho. */
    private enum class RowDensity(val minHeight: Dp) {
        /** Símbolo y cambio arriba, precio abajo. */
        TINY(44.dp),
        /** Símbolo a la izquierda; precio y cambio apilados a la derecha. */
        NARROW(46.dp),
        /** Con nombre, sin minigráfico. */
        MEDIUM(50.dp),
        /** Además, minigráfico. */
        WIDE(50.dp),
        /** Minigráfico más grande. */
        XWIDE(52.dp);

        companion object {
            fun of(rowWidth: Dp) = when {
                rowWidth < 150.dp -> TINY
                rowWidth < 230.dp -> NARROW
                rowWidth < 280.dp -> MEDIUM
                rowWidth < 340.dp -> WIDE
                else -> XWIDE
            }
        }
    }

    /**
     * Una fila como en Yahoo o la app Bolsa: símbolo y nombre a la izquierda, el minigráfico en el
     * medio y a la derecha el precio con el cambio abajo, en una columna angosta para que el
     * nombre tenga lugar.
     */
    @Composable
    private fun QuoteRow(symbol: String, quote: Quote?, density: RowDensity, options: RowOptions) {
        val p = options.palette
        val open = openSymbol(symbol)
        if (density == RowDensity.TINY) {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(open)) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        symbol,
                        maxLines = 1,
                        style = TextStyle(color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (quote != null) {
                        Text(
                            Format.percent(quote.changePercent),
                            maxLines = 1,
                            style = TextStyle(color = if (quote.isUp) p.up else p.down, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                if (quote != null) {
                    Text(Format.price(quote.price), maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                }
            }
            return
        }
        val big = density >= RowDensity.MEDIUM
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    symbol,
                    maxLines = 1,
                    style = TextStyle(color = p.text, fontSize = if (big) 16.sp else 14.sp, fontWeight = FontWeight.Bold),
                )
                if (options.showName && quote != null) {
                    Text(quote.name, maxLines = 1, style = TextStyle(color = p.secondary, fontSize = if (big) 12.sp else 11.sp))
                }
            }
            val sparkWidth = when (density) {
                RowDensity.WIDE -> 56.dp
                RowDensity.XWIDE -> 72.dp
                else -> 0.dp
            }
            if (options.showSpark && sparkWidth > 0.dp && quote != null) {
                Spacer(GlanceModifier.width(8.dp))
                SparkImage(quote, sparkWidth, 28.dp)
                Spacer(GlanceModifier.width(12.dp))
            }
            if (quote == null) {
                Text("—", style = TextStyle(color = p.secondary, fontSize = 15.sp))
                return@Row
            }
            // Columna de ancho fijo: los minigráficos y los precios quedan alineados.
            Column(
                modifier = GlanceModifier.width(if (big) 96.dp else 78.dp),
                horizontalAlignment = Alignment.End,
            ) {
                PriceText(quote, p, if (big) 16.sp else 14.sp)
                ChangeText(quote, p, options.pill, options.showValue, if (big) 13.sp else 12.sp, 0.dp, alignEnd = true, fitContent = true)
                val ext = quote.extChangePercent
                if (options.showExt && big && quote.extSession != null && ext != null) {
                    Row {
                        Text(
                            "${quote.extSession.label} ",
                            maxLines = 1,
                            style = TextStyle(color = p.secondary, fontSize = 11.sp),
                        )
                        Text(
                            Format.percent(ext),
                            maxLines = 1,
                            style = TextStyle(color = if (ext >= 0) p.up else p.down, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }

    /** Mosaico: una baldosa por símbolo, más intensa cuanto más se movió (como un mapa de calor). */
    @Composable
    private fun Mosaic(rows: List<Pair<String, Quote?>>, width: Dp, height: Dp, options: RowOptions) {
        val columns = when {
            width < 170.dp -> 2
            width < 260.dp -> 3
            width < 360.dp -> 4
            else -> 5
        }
        val lines = ceil(rows.size / columns.toDouble()).toInt().coerceAtLeast(1)
        // Si entran todas, se estiran para llenar el widget; si no, se hace scroll.
        val available = height - 60.dp
        val tileHeight = (available / lines - 4.dp).coerceIn(52.dp, 96.dp)
        Grid(rows, columns, fill = false) { (symbol, quote) ->
            Box(modifier = GlanceModifier.padding(2.dp)) {
                Tile(symbol, quote, tileHeight, options)
            }
        }
    }

    @Composable
    private fun Tile(symbol: String, quote: Quote?, height: Dp, options: RowOptions) {
        val white = ColorProvider(Color.White)
        val faded = ColorProvider(Color.White.copy(alpha = 0.85f))
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(height)
                .cornerRadius(12.dp)
                .background(ColorProvider(heatColor(quote?.changePercent)))
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .clickable(openSymbol(symbol)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                symbol,
                maxLines = 1,
                style = TextStyle(color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            if (quote != null) {
                Text(
                    if (options.showValue) Format.change(quote.change) else Format.percent(quote.changePercent),
                    maxLines = 1,
                    style = TextStyle(color = white, fontSize = if (height >= 64.dp) 16.sp else 14.sp, fontWeight = FontWeight.Bold),
                )
                if (height >= 64.dp) {
                    Text(Format.price(quote.price), maxLines = 1, style = TextStyle(color = faded, fontSize = 11.sp))
                }
            }
        }
    }

    data class RowOptions(
        val palette: WidgetPalette,
        val showSpark: Boolean,
        val showName: Boolean,
        val pill: Boolean,
        val showExt: Boolean,
        val showValue: Boolean,
    )

    companion object {
        /** Estado del mercado de la lista (el del primer símbolo que no sea cripto). */
        fun marketLabel(quotes: List<Quote>): Pair<String, Boolean>? {
            val quote = quotes.firstOrNull { it.quoteType != "CRYPTOCURRENCY" && it.marketState != null } ?: return null
            val label = Format.marketState(quote.marketState).takeIf { it.isNotEmpty() } ?: return null
            return label to (quote.marketState == "REGULAR")
        }

        private val NEUTRAL = Color(0xFF59606B)
        private val UP = Color(0xFF15803D)
        private val DOWN = Color(0xFFC0262D)

        /** Gris si casi no se movió; verde o rojo pleno a partir de ±3 %. */
        fun heatColor(percent: Double?): Color {
            if (percent == null) return NEUTRAL
            val strength = (abs(percent) / 3.0).coerceIn(0.0, 1.0).toFloat()
            return lerp(NEUTRAL, if (percent >= 0) UP else DOWN, 0.45f + 0.55f * strength)
        }
    }
}

/** Abre el detalle de [symbol] en la app. */
fun openSymbol(symbol: String): Action =
    actionStartActivity<MainActivity>(actionParametersOf(ActionParameters.Key<String>(MainActivity.EXTRA_SYMBOL) to symbol))

/** Las esquinas del launcher (Android 12+), o 24 dp. */
fun GlanceModifier.widgetCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(24.dp)
    }

@Composable
fun PriceText(quote: Quote, p: WidgetPalette, fontSize: TextUnit, width: Dp? = null) {
    Text(
        Format.price(quote.price),
        maxLines = 1,
        style = TextStyle(color = p.text, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
        modifier = if (width != null) GlanceModifier.width(width) else GlanceModifier,
    )
}

/** El cambio del día: texto de color o etiqueta de color. */
@Composable
fun ChangeText(
    quote: Quote,
    p: WidgetPalette,
    pill: Boolean,
    showValue: Boolean,
    fontSize: TextUnit,
    minWidth: Dp,
    /** Falso debajo de un precio alineado a la izquierda (widget de un símbolo). */
    alignEnd: Boolean = true,
    /** Sin ancho fijo: la etiqueta mide lo que el texto (en la columna de precios de la lista). */
    fitContent: Boolean = false,
) {
    val fixed = alignEnd && !fitContent
    val text = if (showValue) Format.change(quote.change) else Format.percent(quote.changePercent)
    if (pill) {
        Box(
            modifier = GlanceModifier
                .let { if (fixed) it.width(minWidth) else it }
                .cornerRadius(8.dp)
                .background(if (quote.isUp) p.upPill else p.downPill)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
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
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
            modifier = if (fixed) GlanceModifier.width(minWidth) else GlanceModifier,
        )
    }
}

/** El minigráfico del día de [quote], con el cierre anterior punteado. */
@Composable
fun SparkImage(quote: Quote, width: Dp, height: Dp) {
    val density = LocalContext.current.resources.displayMetrics.density
    // Los widgets tienen un límite de memoria para imágenes: nunca más de 600 px de ancho.
    val w = min(width, 600.dp / density)
    val bitmap = remember(quote.spark, quote.previousClose, quote.isUp, w, height) {
        val px = (w.value * density).toInt()
        Sparkline.render(
            prices = Sparkline.downsample(quote.spark, px / 2),
            baseline = quote.previousClose,
            widthPx = px,
            heightPx = (height.value * density).toInt(),
            color = if (quote.isUp) WidgetPalette.UP else WidgetPalette.DOWN,
            baselineColor = WidgetPalette.BASELINE,
            density = density,
        )
    }
    if (bitmap != null) {
        Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(width, height))
    } else {
        Spacer(GlanceModifier.size(width, height))
    }
}
