package com.personal.podpulse.features

import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.data.PodStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Smoothed signal strength of my AirPods, for the hotter/colder finder. */
object FindSignal {
    data class Reading(val rssi: Float, val time: Long)

    private const val RESET_AFTER_MS = 5_000L

    private val _reading = MutableStateFlow<Reading?>(null)
    val reading: StateFlow<Reading?> = _reading.asStateFlow()

    fun offer(status: PodStatus) {
        val myModel = PodRepository.status.value?.modelId
        if (myModel != null && myModel != status.modelId) return
        val prev = _reading.value
        val rssi = if (prev == null || status.timestamp - prev.time > RESET_AFTER_MS) {
            status.rssi.toFloat()
        } else {
            prev.rssi * 0.7f + status.rssi * 0.3f
        }
        _reading.value = Reading(rssi, status.timestamp)
    }

    /** 0 = far away (or no signal), 1 = right next to the phone. */
    fun proximity(rssi: Float): Float = ((rssi + 100f) / 65f).coerceIn(0f, 1f)
}
