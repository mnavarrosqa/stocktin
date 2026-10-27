package app.stocktin.widget

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.unit.ColorProvider

/** Claves del estado que guarda cada widget. */
object WidgetKeys {
    /** Lista que muestra el widget de lista. */
    val LIST_ID = stringPreferencesKey("list_id")
    /** Símbolo del widget de un solo símbolo. */
    val SYMBOL = stringPreferencesKey("symbol")
    val THEME = stringPreferencesKey("theme")
    val SHOW_SPARK = booleanPreferencesKey("show_spark")
    val SHOW_NAME = booleanPreferencesKey("show_name")
    /** Cambio dentro de una etiqueta de color (como en la app de Bolsa de iPhone). */
    val PILL = booleanPreferencesKey("pill")
    /** Mostrar pre-apertura y post-cierre. */
    val SHOW_EXT = booleanPreferencesKey("show_ext")
    /** Cambio en dinero en vez de porcentaje. */
    val SHOW_VALUE = booleanPreferencesKey("show_value")
}

/** Aspecto del widget. */
enum class WidgetTheme(val label: String) {
    SYSTEM("Automático"),
    LIGHT("Claro"),
    DARK("Oscuro"),
    BLACK("Negro"),
    GLASS("Transparente");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/** Colores de un widget según su [WidgetTheme]. */
class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val secondary: ColorProvider,
    val divider: ColorProvider,
    val up: ColorProvider,
    val down: ColorProvider,
    val upPill: ColorProvider,
    val downPill: ColorProvider,
    val onPill: ColorProvider,
) {
    companion object {
        // Mismos colores en la app y en el minigráfico del widget.
        const val UP = 0xFF16A34A.toInt()
        const val DOWN = 0xFFE5484D.toInt()
        const val BASELINE = 0x998A9099.toInt()

        private val light = Colors(
            background = Color(0xFFFFFFFF), text = Color(0xFF14171A), secondary = Color(0xFF5F6670),
            divider = Color(0xFFE8EAED), up = Color(0xFF0F8A43), down = Color(0xFFD62C3B),
        )
        private val dark = Colors(
            background = Color(0xFF1C1E22), text = Color(0xFFF2F4F7), secondary = Color(0xFF9AA1AB),
            divider = Color(0xFF2E3238), up = Color(0xFF3CCB7F), down = Color(0xFFFF6369),
        )
        private val black = dark.copy(background = Color(0xFF000000), divider = Color(0xFF1F2226))
        private val glass = dark.copy(background = Color(0x8C000000), divider = Color(0x33FFFFFF), secondary = Color(0xFFC9CED6))

        fun of(theme: WidgetTheme): WidgetPalette = when (theme) {
            WidgetTheme.SYSTEM -> WidgetPalette(
                background = DayNight(light.background, dark.background),
                text = DayNight(light.text, dark.text),
                secondary = DayNight(light.secondary, dark.secondary),
                divider = DayNight(light.divider, dark.divider),
                up = DayNight(light.up, dark.up),
                down = DayNight(light.down, dark.down),
                upPill = DayNight(Color(0xFF16A34A), Color(0xFF1F9D55)),
                downPill = DayNight(Color(0xFFE5484D), Color(0xFFD9363E)),
                onPill = ColorProvider(Color.White),
            )
            WidgetTheme.LIGHT -> light.palette()
            WidgetTheme.DARK -> dark.palette()
            WidgetTheme.BLACK -> black.palette()
            WidgetTheme.GLASS -> glass.palette()
        }
    }

    private data class Colors(
        val background: Color,
        val text: Color,
        val secondary: Color,
        val divider: Color,
        val up: Color,
        val down: Color,
    ) {
        fun palette() = WidgetPalette(
            background = ColorProvider(background),
            text = ColorProvider(text),
            secondary = ColorProvider(secondary),
            divider = ColorProvider(divider),
            up = ColorProvider(up),
            down = ColorProvider(down),
            upPill = ColorProvider(Color(0xFF16A34A)),
            downPill = ColorProvider(Color(0xFFE5484D)),
            onPill = ColorProvider(Color.White),
        )
    }
}
