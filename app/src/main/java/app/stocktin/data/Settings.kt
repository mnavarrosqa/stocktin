package app.stocktin.data

import android.content.Context

/** Ajustes generales de la app. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Cada cuántos minutos se actualizan los widgets en segundo plano. 0 = solo a mano. */
    var refreshMinutes: Int
        get() = prefs.getInt("refresh_minutes", 15)
        set(value) = prefs.edit().putInt("refresh_minutes", value).apply()

    /** Con el mercado de EE.UU. cerrado, actualizar cada una hora en vez de seguido. */
    var saveBatteryWhenClosed: Boolean
        get() = prefs.getBoolean("save_battery", true)
        set(value) = prefs.edit().putBoolean("save_battery", value).apply()

    var lastRefresh: Long
        get() = prefs.getLong("last_refresh", 0)
        set(value) = prefs.edit().putLong("last_refresh", value).apply()

    /** Lista que estaba abierta en la app. */
    var currentList: String?
        get() = prefs.getString("current_list", null)
        set(value) = prefs.edit().putString("current_list", value).apply()

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean("auto_check_updates", true)
        set(value) = prefs.edit().putBoolean("auto_check_updates", value).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0)
        set(value) = prefs.edit().putLong("last_update_check", value).apply()

    /** Orden de la lista en la app ([SortMode]). */
    var sortMode: String?
        get() = prefs.getString("sort_mode", null)
        set(value) = prefs.edit().putString("sort_mode", value).apply()

    /** Últimas búsquedas, la más reciente primero. */
    var recentSearches: List<String>
        get() = prefs.getString("recent_searches", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString("recent_searches", value.take(8).joinToString("\n")).apply()

    /** Tema de la app: SYSTEM, LIGHT o DARK. */
    var themeMode: String
        get() = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    /** Usar los colores del fondo de pantalla (Material You) en vez del verde de Stocktin. */
    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(value) = prefs.edit().putBoolean("dynamic_color", value).apply()

    /** Última versión de la que se mostraron las novedades (0 = todavía ninguna). */
    var lastSeenVersion: Int
        get() = prefs.getInt("last_seen_version", 0)
        set(value) = prefs.edit().putInt("last_seen_version", value).apply()

    /** Mostrar el cambio en dinero en vez de en porcentaje. */
    var showChangeValue: Boolean
        get() = prefs.getBoolean("show_change_value", false)
        set(value) = prefs.edit().putBoolean("show_change_value", value).apply()

    /** Avisar el día de cada evento de la agenda (Fed, datos de EE.UU. y balances). */
    var agendaNotifications: Boolean
        get() = prefs.getBoolean("agenda_notifications", true)
        set(value) = prefs.edit().putBoolean("agenda_notifications", value).apply()

    /** Eventos de la agenda de los que ya se avisó (los últimos). */
    var notifiedAgenda: Set<String>
        get() = prefs.getString("notified_agenda", null)?.split('\n')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        set(value) = prefs.edit().putString("notified_agenda", value.toList().takeLast(60).joinToString("\n")).apply()

    /** Ya se pidió una vez el permiso de notificaciones al abrir la app. */
    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(value) = prefs.edit().putBoolean("asked_notifications", value).apply()

    /** Ocultar el saldo de la cartera (el ojito, como en las apps de los bancos). */
    var hideBalance: Boolean
        get() = prefs.getBoolean("hide_balance", false)
        set(value) = prefs.edit().putBoolean("hide_balance", value).apply()

    /** Pedir huella, cara o el PIN del teléfono al abrir la app. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(value) = prefs.edit().putBoolean("app_lock", value).apply()
}
