package com.nauuman.measure.data

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** Formats lengths (meters) and areas (square meters) for display. */
object Units {
    private const val M_PER_INCH = 0.0254f

    fun length(meters: Float, imperial: Boolean): String {
        val l = Locale.getDefault()
        return if (!imperial) {
            when {
                meters < 0.1f -> String.format(l, "%.1f cm", meters * 100f)
                meters < 1f -> String.format(l, "%.0f cm", meters * 100f)
                else -> String.format(l, "%.2f m", meters)
            }
        } else {
            val inches = meters / M_PER_INCH
            if (inches < 12f) {
                formatInches(inches)
            } else {
                var feet = floor(inches / 12f).toInt()
                var rest = (inches - feet * 12f).roundToInt()
                if (rest == 12) {
                    feet += 1
                    rest = 0
                }
                "$feet′ $rest″"
            }
        }
    }

    /** Inches rounded to the nearest quarter, e.g. 5¼″. */
    private fun formatInches(inches: Float): String {
        val quarters = (inches * 4f).roundToInt()
        val whole = quarters / 4
        val frac = when (quarters % 4) {
            1 -> "¼"
            2 -> "½"
            3 -> "¾"
            else -> ""
        }
        return if (whole == 0 && frac.isNotEmpty()) "$frac″" else "$whole$frac″"
    }

    fun area(squareMeters: Float, imperial: Boolean): String {
        val l = Locale.getDefault()
        return if (!imperial) {
            if (squareMeters < 0.1f) String.format(l, "%.0f cm²", squareMeters * 10_000f)
            else String.format(l, "%.2f m²", squareMeters)
        } else {
            val sqft = squareMeters * 10.7639f
            if (sqft < 1f) String.format(l, "%.0f in²", squareMeters * 1550.0031f)
            else String.format(l, "%.2f ft²", sqft)
        }
    }
}
