package app.stocktin.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stocktin.alerts.AgendaNotifier
import app.stocktin.alerts.AlertNotifier
import app.stocktin.data.MarketHours
import app.stocktin.data.Repository
import app.stocktin.data.Settings
import java.util.concurrent.TimeUnit

object Widgets {

    suspend fun updateAll(context: Context) {
        runCatching {
            WatchlistWidget().updateAll(context)
            TickerWidget().updateAll(context)
        }
    }

    /** Los símbolos de todas las listas, de los widgets de un símbolo, de las alertas y de la cartera. */
    suspend fun symbolsInUse(context: Context): List<String> {
        // Si no se pueden leer los widgets, igual se actualizan las listas.
        val tickers = runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(TickerWidget::class.java).mapNotNull { id ->
                getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetKeys.SYMBOL]
            }
        }.getOrDefault(emptyList())
        val repository = Repository.get(context)
        return (repository.allSymbols() + tickers + repository.alerts.value.map { it.symbol } + repository.holdings.value.keys).distinct()
    }

    /** Trae cotizaciones nuevas (más [extra], si se pide), avisa las alertas cumplidas y redibuja los widgets. */
    suspend fun refresh(context: Context, extra: List<String> = emptyList()): Result<Unit> {
        val repository = Repository.get(context)
        val result = repository.refresh((symbolsInUse(context) + extra).distinct())
        if (result.isSuccess) AlertNotifier.notify(context, repository.takeReachedAlerts())
        repository.refreshCalendar()
        AgendaNotifier.notifyToday(context)
        updateAll(context)
        return result
    }
}

/** El botón ↻ del widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Widgets.refresh(context)
    }
}

/** Actualiza las cotizaciones en segundo plano. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val repository = Repository.get(applicationContext)
        // Con la bolsa cerrada (y sin cripto, que opera siempre) alcanza con una vez por hora.
        val hasCrypto = repository.quotes.value.values.any { it.quoteType == "CRYPTOCURRENCY" }
        val recent = System.currentTimeMillis() - settings.lastRefresh < TimeUnit.MINUTES.toMillis(55)
        val periodic = tags.contains(RefreshScheduler.PERIODIC)
        if (periodic && settings.saveBatteryWhenClosed && !hasCrypto && !MarketHours.isUsActive() && recent) {
            return Result.success()
        }
        Widgets.refresh(applicationContext)
        return Result.success()
    }
}

object RefreshScheduler {

    const val PERIODIC = "refresh-periodic"
    private const val NOW = "refresh-now"

    /** Programa la actualización periódica según Ajustes (o la cancela si es manual). */
    fun schedule(context: Context) {
        val minutes = Settings(context).refreshMinutes
        val work = WorkManager.getInstance(context)
        if (minutes <= 0) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(PERIODIC)
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Una actualización ya (por ejemplo, al poner un widget nuevo). */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}

abstract class StocktinWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.schedule(context)
        RefreshScheduler.refreshNow(context)
    }
}

class WatchlistWidgetReceiver : StocktinWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchlistWidget()
}

class TickerWidgetReceiver : StocktinWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TickerWidget()
}
