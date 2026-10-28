package app.stocktin.screenshots

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
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
import app.stocktin.data.Repository
import app.stocktin.data.YahooApi
import app.stocktin.ui.MainActivity
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature as fakeFoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
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

    /** Para simular la bisagra de un foldable. */
    @get:Rule
    val windowLayout = WindowLayoutInfoPublisherRule()

    @Before
    fun setUp() {
        YahooApi.fakeResponses = FakeYahoo::respond
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_check_updates", false)
            .putBoolean("asked_notifications", true)
            .putString("recent_searches", "MELI\nTSLA")
            .commit()
        // Una cartera chica para que se vean la tarjeta de arriba y "Tu posición".
        Repository.get(context).setHolding("NVDA", 12.0, 160.0)
        Repository.get(context).setHolding("AAPL", 5.0, 245.0)
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

    /** Baja la lista hasta [symbol] (la tarjeta de la cartera lo puede dejar fuera de la pantalla) y lo abre. */
    private fun openSymbol(symbol: String) {
        val list = compose.onAllNodes(hasScrollToNodeAction()).onFirst()
        list.performScrollToNode(hasText(symbol))
        // Queda pegado abajo, debajo de la barra flotante: se sube un poco más.
        list.performTouchInput { swipeUp(startY = bottom * 0.6f, endY = bottom * 0.3f) }
        settle()
        compose.onAllNodesWithText(symbol).onFirst().performClick()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("screenshots/$name.png")

    @Test
    fun lists() {
        launch()
        shot("01_listas")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun suggestions() {
        launch()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Ideas para armar listas"))
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performTouchInput { swipeUp(startY = bottom * 0.7f, endY = bottom * 0.35f) }
        settle()
        shot("01c_ideas_de_listas")
        compose.onNodeWithText("Las 7 magníficas").performClick()
        settle()
        captureScreenRoboImage("screenshots/01d_lista_sugerida.png")
    }

    @Test
    fun listsHiddenBalance() {
        launch()
        compose.onNodeWithContentDescription("Ocultar saldo").performClick()
        settle()
        shot("01b_listas_saldo_oculto")
        // Se deja como estaba para las demás capturas.
        compose.onNodeWithContentDescription("Mostrar saldo").performClick()
        settle()
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
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        shot("03b_mercados_abajo")
    }

    @Test
    fun detail() {
        launch()
        openSymbol("NVDA")
        settle()
        shot("04_detalle")
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        shot("05_detalle_abajo")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun agendaEvent() {
        launch()
        compose.onNodeWithContentDescription("Mercados").performClick()
        settle()
        // El primer evento de la agenda que no sea un balance (depende del día en que corre).
        val label = listOf("Empleo", "Inflación", "PBI", "Fed").first { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(label).onFirst().performClick()
        settle()
        captureScreenRoboImage("screenshots/03c_evento_agenda.png")
    }

    @Test
    fun compare() {
        launch()
        openSymbol("NVDA")
        settle()
        compose.onNodeWithText("vs S&P 500").performClick()
        settle()
        shot("04c_detalle_vs_sp500")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun alert() {
        launch()
        openSymbol("NVDA")
        settle()
        compose.onNodeWithContentDescription("Nueva alerta de precio").performClick()
        settle()
        // La hoja va en otra ventana: se captura la pantalla entera.
        captureScreenRoboImage("screenshots/04b_nueva_alerta.png")
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
        compose.onRoot().performTouchInput { swipeUp() }
        settle()
        shot("07b_ajustes_abajo")
    }

    @Test
    @Config(qualifiers = UNFOLDED)
    fun unfolded() {
        launch()
        openSymbol("QQQ")
        settle()
        shot("08_desplegado")
    }

    /** Foldable desplegado con la bisagra vertical en el medio (modo libro), como un Galaxy Z Fold. */
    @Test
    @Config(qualifiers = FOLD)
    fun foldBook() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            val hinge = fakeFoldingFeature(activity = activity, size = 20, state = FoldingFeature.State.FLAT, orientation = FoldingFeature.Orientation.VERTICAL)
            windowLayout.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(hinge)))
        }
        settle()
        shot("09_fold_libro")
        openSymbol("NVDA")
        settle()
        shot("10_fold_libro_detalle")
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tablet() {
        launch()
        openSymbol("NVDA")
        settle()
        shot("11_tablet")
    }
}

const val FOLD = "w690dp-h800dp-xhdpi"
const val TABLET = "w1280dp-h800dp-land-mdpi"
private const val PHONE_SIZE = "w411dp-h914dp"
const val PHONE = "$PHONE_SIZE-xxhdpi"
const val UNFOLDED = "w841dp-h701dp-land-xhdpi"
