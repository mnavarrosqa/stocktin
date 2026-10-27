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

    /** Mostrar el cambio en dinero en vez de en porcentaje. */
    var showChangeValue: Boolean
        get() = prefs.getBoolean("show_change_value", false)
        set(value) = prefs.edit().putBoolean("show_change_value", value).apply()
}
