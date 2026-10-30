package app.stocktin.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Formatos de números y horas. Los precios usan el formato de Yahoo (1,234.56). */
object Format {

    private val symbols = DecimalFormatSymbols(Locale.US)
    private val spanish = Locale.forLanguageTag("es-AR")

    fun price(value: Double): String {
        val pattern = when {
            abs(value) >= 1 -> "#,##0.00"
            abs(value) >= 0.01 -> "0.0000"
            else -> "0.00######"
        }
        return DecimalFormat(pattern, symbols).format(value)
    }

    /** Pesos como en Argentina: 28.880,00. */
    fun pesos(value: Double): String = DecimalFormat("#,##0.00", DecimalFormatSymbols(spanish)).format(value)

    /** Un precio en su moneda: los pesos como en Argentina, lo demás como Yahoo. */
    fun price(value: Double, currency: String?): String = if (currency == "ARS") pesos(value) else price(value)

    /** "+1.234,50" en pesos o "+1,234.50" en lo demás. */
    fun change(value: Double, currency: String?): String = sign(value) + price(abs(value), currency)

    fun percent(value: Double): String = sign(value) + DecimalFormat("0.00", symbols).format(abs(value)) + "%"

    fun change(value: Double): String = sign(value) + price(abs(value))

    private fun sign(value: Double) = if (value < 0 && abs(value) >= 0.005) "-" else "+"

    /** 1.2K, 3.4M, 5.6B, 7.8T. */
    fun compact(value: Double): String {
        val a = abs(value)
        val (divisor, suffix) = when {
            a >= 1e12 -> 1e12 to "T"
            a >= 1e9 -> 1e9 to "B"
            a >= 1e6 -> 1e6 to "M"
            a >= 1e3 -> 1e3 to "K"
            else -> return DecimalFormat("#,##0", symbols).format(value)
        }
        return DecimalFormat("0.##", symbols).format(value / divisor) + suffix
    }

    /** "9:42" si es de hoy, "27 oct 9:42" si no. */
    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
        val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val pattern = if (moment.toLocalDate() == today) "H:mm" else "d MMM H:mm"
        return DateTimeFormatter.ofPattern(pattern, spanish).format(moment).replace(".", "")
    }

    /** Estado del mercado en pocas palabras. */
    fun marketState(state: String?): String = when (state) {
        "REGULAR" -> "Abierto"
        "PRE", "PREPRE" -> "Pre-apertura"
        "POST", "POSTPOST" -> "Post-cierre"
        "CLOSED" -> "Cerrado"
        else -> ""
    }
}
