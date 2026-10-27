package com.personal.podpulse.data

import android.content.Context

/**
 * Estimates time to a full charge. AirPods only report battery in 10 % steps,
 * so it starts from typical charging speeds and learns the real speed of your
 * AirPods from how long each 10 % step takes while charging.
 */
object ChargeEstimator {
    enum class Part(val key: String, val defaultMinutesPerStep: Double) {
        LEFT("left", 6.0),
        RIGHT("right", 6.0),
        CASE("case", 12.0),
    }

    private const val PREFS = "charge_rates"

    /** Charging slows down near the top, like on a phone. */
    private const val TAPER_FROM = 80
    private const val TAPER_FACTOR = 1.5

    private class Tracker(var level: Int? = null, var stepStartedAt: Long = 0L)

    private val trackers = Part.entries.associateWith { Tracker() }

    /** Pure estimate, in minutes, from [level] to 100 %. */
    fun estimateMinutes(level: Int, minutesPerStep: Double): Int {
        var total = 0.0
        var l = level
        while (l < 100) {
            total += minutesPerStep * (if (l >= TAPER_FROM) TAPER_FACTOR else 1.0)
            l += 10
        }
        return total.toInt()
    }

    fun minutesToFull(context: Context, part: Part, level: Int): Int =
        estimateMinutes(level, rate(context, part))

    private fun rate(context: Context, part: Part): Double =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(part.key, part.defaultMinutesPerStep.toFloat())
            .toDouble()

    @Synchronized
    fun observe(context: Context, s: PodStatus) {
        update(context, Part.LEFT, s.left, s.leftCharging, s.timestamp)
        update(context, Part.RIGHT, s.right, s.rightCharging, s.timestamp)
        update(context, Part.CASE, s.case, s.caseCharging, s.timestamp)
    }

    private fun update(context: Context, part: Part, level: Int?, charging: Boolean, now: Long) {
        val t = trackers.getValue(part)
        if (level == null || !charging) {
            t.level = level
            t.stepStartedAt = 0L
            return
        }
        val previous = t.level
        if (previous == level) return
        // A full 10 % step observed from start to finish while charging: learn from it.
        if (previous != null && level == previous + 10 && t.stepStartedAt > 0L) {
            var minutes = (now - t.stepStartedAt) / 60_000.0
            if (previous >= TAPER_FROM) minutes /= TAPER_FACTOR
            if (minutes in 1.0..60.0) {
                val learned = rate(context, part) * 0.6 + minutes * 0.4
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putFloat(part.key, learned.toFloat())
                    .apply()
            }
        }
        t.level = level
        t.stepStartedAt = now
    }

    fun format(minutes: Int): String = when {
        minutes <= 0 -> "Fully charged"
        minutes < 60 -> "Full in ~$minutes min"
        else -> "Full in ~${minutes / 60} h ${minutes % 60} min"
    }
}
