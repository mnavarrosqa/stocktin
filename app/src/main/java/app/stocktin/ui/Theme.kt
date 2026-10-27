package app.stocktin.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Up = Color(0xFF16A34A)
val Down = Color(0xFFE5484D)

private val Light = lightColorScheme(
    primary = Color(0xFF0E7C5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE1),
    onPrimaryContainer = Color(0xFF002116),
    background = Color(0xFFF8FAF9),
    surface = Color(0xFFF8FAF9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6FDBB0),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFCDEFE1),
    background = Color(0xFF111413),
    surface = Color(0xFF111413),
)

/** Colores del sistema (Material You) si el teléfono los tiene; si no, un verde propio. */
@Composable
fun StocktinTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun changeColor(value: Double): Color = if (value >= 0) Up else Down
