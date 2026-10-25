package com.personal.podpulse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the phone was when the AirPods last disconnected. */
data class Place(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val time: Long,
)

object LastSeen {
    private const val PREFS = "last_seen"

    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()

    private val _disconnectedAt = MutableStateFlow<Long?>(null)
    val disconnectedAt: StateFlow<Long?> = _disconnectedAt.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _disconnectedAt.value = p.getLong("disconnected_at", 0L).takeIf { it > 0 }
        if (p.contains("lat")) {
            _place.value = Place(
                latitude = Double.fromBits(p.getLong("lat", 0L)),
                longitude = Double.fromBits(p.getLong("lon", 0L)),
                accuracyMeters = p.getFloat("accuracy", 0f),
                time = p.getLong("time", 0L),
            )
        }
    }

    fun markDisconnected(context: Context, time: Long) {
        _disconnectedAt.value = time
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("disconnected_at", time)
            .apply()
    }

    fun save(context: Context, place: Place) {
        _place.value = place
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("lat", place.latitude.toRawBits())
            .putLong("lon", place.longitude.toRawBits())
            .putFloat("accuracy", place.accuracyMeters)
            .putLong("time", place.time)
            .apply()
    }
}
