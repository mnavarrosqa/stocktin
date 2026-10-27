package app.stocktin.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.widget.Toast
import androidx.core.content.IntentCompat

/** Recibe cómo va la instalación de una actualización. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // Android necesita que el usuario confirme: se abre su pantalla de instalación.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (message?.contains("UPDATE_INCOMPATIBLE") == true) {
                    // La copia instalada se firmó con otra clave (por ejemplo, un APK de prueba): Android
                    // no deja instalar encima. Hay que desinstalarla y bajar la versión oficial.
                    Toast.makeText(
                        context,
                        "Esta copia es de prueba: desinstalala y bajá la versión oficial",
                        Toast.LENGTH_LONG,
                    ).show()
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    Toast.makeText(context, "No se pudo actualizar${message?.let { ": $it" } ?: ""}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

private const val RELEASES_URL = "https://github.com/mnavarrosqa/stocktin/releases/latest"
