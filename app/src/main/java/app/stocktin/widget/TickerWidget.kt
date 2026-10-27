package app.stocktin.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
import app.stocktin.data.Repository
import app.stocktin.ui.MainActivity

/** Widget chico de un solo símbolo: precio grande, cambio y gráfico del día. */
class TickerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Repository.get(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val quotes by repository.quotes.collectAsState()
            val symbol = prefs[WidgetKeys.SYMBOL] ?: repository.allSymbols().firstOrNull() ?: "SPY"
            val quote = quotes[symbol]
            val p = WidgetPalette.of(WidgetTheme.from(prefs[WidgetKeys.THEME]))
            val pill = prefs[WidgetKeys.PILL] ?: false
            val showValue = prefs[WidgetKeys.SHOW_VALUE] ?: false
            val size = LocalSize.current
            val small = size.height < 110.dp

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .cornerRadius(24.dp)
                    .background(p.background)
                    .padding(12.dp)
                    .clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(ActionParameters.Key<String>(MainActivity.EXTRA_SYMBOL) to symbol),
                        ),
                    ),
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        symbol,
                        maxLines = 1,
                        style = TextStyle(color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (quote != null && quote.isOpen) {
                        Text("●", style = TextStyle(color = p.up, fontSize = 10.sp))
                    }
                }
                if (quote == null) {
                    Text("Sin datos todavía", style = TextStyle(color = p.secondary, fontSize = 12.sp))
                    return@Column
                }
                if (!small) {
                    Text(quote.name, maxLines = 1, style = TextStyle(color = p.secondary, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.defaultWeight())
                if (!small) {
                    SparkImage(quote, size.width - 24.dp, (size.height * 0.3f).coerceAtMost(60.dp))
                    Spacer(GlanceModifier.height(6.dp))
                }
                Text(
                    Format.price(quote.price),
                    maxLines = 1,
                    style = TextStyle(color = p.text, fontSize = if (small) 18.sp else 22.sp, fontWeight = FontWeight.Bold),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChangeText(quote, p, pill, showValue, 13.sp, 72.dp)
                    val ext = quote.extChangePercent
                    if (quote.extSession != null && ext != null && size.width >= 150.dp) {
                        Text(
                            "  ${quote.extSession.label} ",
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
}
