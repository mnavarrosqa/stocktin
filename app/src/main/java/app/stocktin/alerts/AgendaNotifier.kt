package app.stocktin.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.stocktin.R
import app.stocktin.data.Agenda
import app.stocktin.data.AgendaEvent
import app.stocktin.data.AgendaKind
import app.stocktin.data.Format
import app.stocktin.data.Repository
import app.stocktin.data.Settings
import app.stocktin.ui.MainActivity
import java.time.LocalTime

/** Avisa a la mañana lo que hay hoy en la agenda: la Fed, datos de EE.UU. y balances de tus símbolos. */
object AgendaNotifier {

    private const val CHANNEL = "agenda"

    fun notifyToday(context: Context, now: Long = System.currentTimeMillis()) {
        val settings = Settings(context)
        if (!settings.agendaNotifications || !AlertNotifier.canNotify(context)) return
        // No a la madrugada: desde las 7 de la mañana.
        if (LocalTime.now().isBefore(LocalTime.of(7, 0))) return
        val due = Agenda.dueToday(Repository.get(context).agenda(), now, settings.notifiedAgenda)
        if (due.isEmpty()) return
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        due.forEach { event ->
            runCatching { manager.notify(event.key.hashCode(), build(context, event)) }
        }
        settings.notifiedAgenda = settings.notifiedAgenda + due.map { it.key }
    }

    private fun build(context: Context, event: AgendaEvent): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Un balance abre el símbolo; lo demás, la agenda en Mercados.
        if (event.symbol != null) intent.putExtra(MainActivity.EXTRA_SYMBOL, event.symbol) else intent.putExtra(MainActivity.EXTRA_TAB, "MARKETS")
        val open = PendingIntent.getActivity(
            context, event.key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = if (event.estimated) "hoy (fecha estimada)" else "hoy a las ${Format.time(event.time)}"
        val text = when (event.kind) {
            AgendaKind.EARNINGS -> "${event.detail ?: event.symbol} presenta resultados $time. Suele moverse fuerte."
            AgendaKind.FED -> "La Fed anuncia las tasas $time.${event.detail?.let { " $it." }.orEmpty()} Mueve a todo el mercado."
            else -> "Sale $time. Puede mover al mercado."
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0F8A69.toInt())
            .setContentTitle(event.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "Agenda del mercado", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "El día de una decisión de la Fed, un dato importante de EE.UU. o el balance de tus acciones"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
