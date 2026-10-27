package app.stocktin.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.stocktin.R
import app.stocktin.data.Alert
import app.stocktin.data.Format
import app.stocktin.data.Quote
import app.stocktin.ui.MainActivity

/** Las notificaciones de las alertas de precio. */
object AlertNotifier {

    private const val CHANNEL = "price-alerts"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, reached: List<Pair<Alert, Quote>>) {
        if (reached.isEmpty() || !canNotify(context)) return
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        reached.forEach { (alert, quote) ->
            val open = PendingIntent.getActivity(
                context,
                alert.id.hashCode(),
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SYMBOL, alert.symbol)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val verb = if (alert.above) "subió a" else "bajó a"
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF0F8A69.toInt())
                .setContentTitle("${alert.symbol} $verb ${Format.price(quote.price)}")
                .setContentText(
                    "Tu alerta era ${Format.price(alert.target)}. Hoy ${Format.percent(quote.changePercent)} · ${quote.name}",
                )
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(alert.id.hashCode(), notification) }
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "Alertas de precio", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Cuando un símbolo llega al precio que marcaste"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
