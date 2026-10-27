package app.stocktin.screenshots

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.stocktin.data.YahooApi
import app.stocktin.ui.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Capturas de pantalla de la app con datos armados ([FakeYahoo]). Se generan en CI con
 * `./gradlew recordRoborazziDebug` y quedan en la rama `screenshots`, para revisar la interfaz
 * sin un teléfono a mano.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class ScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun setUp() {
        YahooApi.fakeResponses = FakeYahoo::respond
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_check_updates", false)
            .putString("recent_searches", "MELI\nTSLA")
            .commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun launch() {
        ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    /** Deja que lleguen las respuestas (en otros hilos) y que terminen las animaciones. */
    private fun settle() {
        repeat(6) {
            compose.waitForIdle()
            Thread.sleep(200)
            ShadowLooper.idleMainLooper()
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("screenshots/$name.png")

    @Test
    fun lists() {
        launch()
        shot("01_listas")
    }

    @Test
    @Config(qualifiers = "$PHONE_SIZE-night-xxhdpi")
    fun listsDark() {
        launch()
        shot("02_listas_oscuro")
    }

    @Test
    fun markets() {
        launch()
        compose.onNodeWithContentDescription("Mercados").performClick()
        settle()
        shot("03_mercados")
    }

    @Test
    fun detail() {
        launch()
        compose.onNodeWithText("NVDA").performClick()
        settle()
        shot("04_detalle")
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        shot("05_detalle_abajo")
    }

    @Test
    fun search() {
        launch()
        compose.onNodeWithContentDescription("Buscar").performClick()
        settle()
        shot("06_busqueda")
    }

    @Test
    fun settings() {
        launch()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        settle()
        shot("07_ajustes")
    }

    @Test
    @Config(qualifiers = UNFOLDED)
    fun unfolded() {
        launch()
        compose.onNodeWithText("NVDA").performClick()
        settle()
        shot("08_desplegado")
    }
}

private const val PHONE_SIZE = "w411dp-h914dp"
const val PHONE = "$PHONE_SIZE-xxhdpi"
const val UNFOLDED = "w841dp-h701dp-land-xhdpi"
