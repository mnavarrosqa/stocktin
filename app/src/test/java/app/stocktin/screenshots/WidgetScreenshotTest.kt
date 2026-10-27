package app.stocktin.screenshots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stocktin.data.Quote
import app.stocktin.data.YahooParser
import app.stocktin.widget.TickerWidget
import app.stocktin.widget.WatchlistWidget
import app.stocktin.widget.WidgetLayout
import app.stocktin.widget.WidgetPalette
import app.stocktin.widget.WidgetTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.net.URLEncoder

/** Capturas de los widgets en distintos tamaños y aspectos, sobre un fondo de pantalla de mentira. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class WidgetScreenshotTest {

    private val symbols = listOf("SPY", "QQQ", "^GSPC", "^NDX", "NVDA", "AAPL", "BTC-USD", "^VIX", "MELI", "TSLA")

    private fun quote(symbol: String): Quote {
        val s = URLEncoder.encode(symbol, "UTF-8")
        val q = YahooParser.parseQuotes(FakeYahoo.respond("https://q/v7/finance/quote?symbols=$s&formatted=false")).first()
        val chart = YahooParser.parseChart(FakeYahoo.respond("https://q/v8/finance/chart/$s?range=1d&interval=5m"))
        return q.copy(spark = YahooParser.regularSession(chart))
    }

    private val rows by lazy { symbols.map { it to quote(it) } }

    private fun render(name: String, width: Dp, height: Dp, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val context: Context = activity
        val result = runBlocking { GlanceRemoteViews().compose(context, DpSize(width, height)) { GlanceTheme { content() } } }
        val density = activity.resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val frame = FrameLayout(activity).apply {
            // Un fondo de pantalla con degradé, para ver bien los bordes y el aspecto Transparente.
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3B5BDB.toInt(), 0xFF7048E8.toInt(), 0xFFE64980.toInt()),
            )
            setPadding(pad, pad, pad, pad)
        }
        val view = result.remoteViews.apply(activity, frame)
        frame.addView(view, FrameLayout.LayoutParams((width.value * density).toInt(), (height.value * density).toInt()))
        activity.setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        repeat(3) { ShadowLooper.idleMainLooper() }
        frame.captureRoboImage("screenshots/widgets/$name.png")
    }

    @Composable
    private fun ListWidget(theme: WidgetTheme, layout: WidgetLayout = WidgetLayout.LIST, pill: Boolean = false, count: Int = 8) {
        val list = rows.take(count)
        WatchlistWidget(scrollable = false).Content(
            title = "Mi lista",
            status = "14:22",
            market = WatchlistWidget.marketLabel(list.map { it.second }),
            rows = list,
            layout = layout,
            options = WatchlistWidget.RowOptions(
                palette = WidgetPalette.of(theme),
                showSpark = true,
                showName = true,
                pill = pill,
                showExt = true,
                showValue = false,
            ),
        )
    }

    @Composable
    private fun TickerWidgetOf(symbol: String, theme: WidgetTheme = WidgetTheme.DYNAMIC) {
        TickerWidget().Content(symbol, rows.toMap()[symbol], WidgetPalette.of(theme), pill = false, showValue = false)
    }

    @Test fun list4x4() = render("01_lista_4x5", 340.dp, 560.dp) { ListWidget(WidgetTheme.DYNAMIC, count = 9) }

    @Test
    @Config(qualifiers = "w411dp-h914dp-night-xxhdpi")
    fun list4x4Night() = render("02_lista_4x4_noche", 340.dp, 400.dp) { ListWidget(WidgetTheme.DYNAMIC, pill = true) }

    @Test fun list4x2() = render("03_lista_4x2", 340.dp, 190.dp) { ListWidget(WidgetTheme.SYSTEM, pill = true) }

    @Test fun list2x2() = render("04_lista_2x2", 160.dp, 190.dp) { ListWidget(WidgetTheme.SYSTEM) }

    @Test fun list3x3() = render("05_lista_3x3", 250.dp, 280.dp) { ListWidget(WidgetTheme.BLACK) }

    @Test fun listGlass() = render("06_lista_transparente", 340.dp, 300.dp) { ListWidget(WidgetTheme.GLASS) }

    @Test
    @Config(qualifiers = TABLET)
    fun listWide() = render("07_lista_tablet", 560.dp, 300.dp) { ListWidget(WidgetTheme.DYNAMIC, count = 10) }

    @Test fun mosaic() = render("08_mosaico_4x3", 340.dp, 290.dp) { ListWidget(WidgetTheme.DYNAMIC, layout = WidgetLayout.MOSAIC, count = 10) }

    @Test fun mosaicSmall() = render("09_mosaico_2x2", 160.dp, 190.dp) { ListWidget(WidgetTheme.DARK, layout = WidgetLayout.MOSAIC, count = 6) }

    @Test fun ticker1x1() = render("10_simbolo_1x1", 76.dp, 76.dp) { TickerWidgetOf("NVDA") }

    @Test fun ticker4x1() = render("11_simbolo_4x1", 340.dp, 90.dp) { TickerWidgetOf("BTC-USD") }

    @Test fun ticker2x2() = render("12_simbolo_2x2", 160.dp, 180.dp) { TickerWidgetOf("AAPL") }

    @Test fun ticker2x2Dark() = render("13_simbolo_2x2_oscuro", 160.dp, 180.dp) { TickerWidgetOf("NVDA", WidgetTheme.DARK) }
}
