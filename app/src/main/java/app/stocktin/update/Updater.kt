package app.stocktin.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en GitHub. */
data class Release(
    val versionCode: Int,
    val versionName: String,
    /** Novedades, una por renglón. */
    val notes: List<String>,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Busca versiones nuevas en las releases de GitHub. Como todas las versiones se firman con la misma
 * clave, se instalan encima sin perder nada.
 */
class Updater(context: Context) {

    private val appContext = context.applicationContext

    val installedVersionCode: Int
        get() = appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()

    val installedVersionName: String
        get() = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()

    /** La última versión publicada, o null si no hay una más nueva que la instalada. */
    suspend fun checkForUpdate(): Release? = withContext(Dispatchers.IO) {
        val release = latestRelease()
        release.takeIf { it.versionCode > installedVersionCode }
    }

    private fun latestRelease(): Release {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val json = try {
            if (connection.responseCode !in 200..299) error("GitHub respondió ${connection.responseCode}")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        val tag = json.getString("tag_name")
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?: error("La versión $tag no tiene APK")
        return Release(
            versionCode = versionCodeOf(tag) ?: error("No se entiende la versión $tag"),
            versionName = tag.removePrefix("v"),
            notes = parseNotes(json.optString("body")),
            apkUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    /**
     * Abre la descarga del APK en el navegador; al terminar, Android lo instala encima. La app no
     * instala nada por su cuenta: sin el permiso para instalar apps, Play Protect no la confunde
     * con las apps falsas que se actualizan solas para meter código.
     */
    fun downloadIntent(release: Release): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/mnavarrosqa/stocktin/releases/latest"

        /** Las versiones se publican como "v0.1.<versionCode>". */
        fun versionCodeOf(tag: String): Int? = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull()

        /** Pasa las notas de la release (markdown simple) a renglones de texto. */
        fun parseNotes(body: String): List<String> =
            body.lines()
                .map { it.trim() }
                .filter { it.startsWith("- ") || it.startsWith("* ") }
                .map { it.drop(2).trim() }
                .filter { it.isNotEmpty() }
    }
}
