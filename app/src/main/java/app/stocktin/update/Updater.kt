package app.stocktin.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    /** Fecha de publicación (ISO 8601), si GitHub la dio. */
    val publishedAt: String? = null,
)

/** Cómo va la descarga de una versión nueva. */
data class DownloadProgress(val running: Boolean, val done: Boolean, val failed: Boolean, val fraction: Float?)

/**
 * Busca versiones nuevas en las releases de GitHub y las descarga con el administrador de descargas
 * de Android. La app no instala nada por su cuenta: al terminar, Android muestra la notificación de
 * la descarga y tocándola se instala encima (todas las versiones se firman con la misma clave).
 * Sin el permiso para instalar apps, Play Protect no confunde a Stocktin con una app dañina.
 */
class Updater(context: Context) {

    private val appContext = context.applicationContext
    private val downloads = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val installedVersionCode: Int
        get() = appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()

    val installedVersionName: String
        get() = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()

    /** La última versión publicada, o null si no hay una más nueva que la instalada. */
    suspend fun checkForUpdate(): Release? = withContext(Dispatchers.IO) {
        parseRelease(getJson("$API/releases/latest") as JSONObject).takeIf { it.versionCode > installedVersionCode }
    }

    /** Las últimas versiones publicadas, la más nueva primero. */
    suspend fun releases(limit: Int = 15): List<Release> = withContext(Dispatchers.IO) {
        val array = getJson("$API/releases?per_page=$limit") as JSONArray
        (0 until array.length()).mapNotNull { runCatching { parseRelease(array.getJSONObject(it)) }.getOrNull() }
    }

    /** La versión [versionCode], para mostrar sus novedades después de actualizar. */
    suspend fun release(versionCode: Int): Release? = withContext(Dispatchers.IO) {
        runCatching { parseRelease(getJson("$API/releases/tags/v0.1.$versionCode") as JSONObject) }.getOrNull()
    }

    /** Empieza a descargar [release] a la carpeta Descargas y devuelve el número de la descarga. */
    fun startDownload(release: Release): Long {
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Stocktin ${release.versionName}")
            .setDescription("Tocá para instalar la actualización")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "stocktin-${release.versionName}.apk")
        return downloads.enqueue(request)
    }

    fun progress(id: Long): DownloadProgress? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            DownloadProgress(
                running = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_PAUSED,
                done = status == DownloadManager.STATUS_SUCCESSFUL,
                failed = status == DownloadManager.STATUS_FAILED,
                fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
            )
        }

    /** Borra el APK descargado (ya instalado o abandonado). */
    fun removeDownload(id: Long) {
        runCatching { downloads.remove(id) }
    }

    /** La lista de descargas de Android: desde ahí se toca el APK para instalarlo. */
    fun downloadsIntent(): Intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Por si el administrador de descargas no está disponible: la descarga en el navegador. */
    fun browserIntent(release: Release): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))

    private fun getJson(url: String): Any {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub respondió ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): Release {
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
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    companion object {
        private const val API = "https://api.github.com/repos/mnavarrosqa/stocktin"
        private const val APK_MIME = "application/vnd.android.package-archive"
        const val RELEASES_PAGE = "https://github.com/mnavarrosqa/stocktin/releases"
        const val REPO_PAGE = "https://github.com/mnavarrosqa/stocktin"

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
