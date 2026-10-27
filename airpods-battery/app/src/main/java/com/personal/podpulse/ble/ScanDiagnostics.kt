package com.personal.podpulse.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One decoded AirPods packet, kept for troubleshooting. */
data class RawPacket(val hex: String, val rssi: Int, val decoded: String)

/** What the scanner has actually seen, shown in the app to troubleshoot. */
data class Diagnostics(
    val liveScan: String = "Not started",
    val anyPackets: Int = 0,
    val applePackets: Int = 0,
    val podPackets: Int = 0,
    val appleTypes: Set<Int> = emptySet(),
    val recent: List<RawPacket> = emptyList(),
    val lastPodParsed: Boolean? = null,
)

/**
 * Packets arrive hundreds of times a second, so counts are gathered here and
 * published to the UI at most twice a second.
 */
object ScanDiagnostics {
    private const val PUBLISH_INTERVAL_MS = 500L
    private const val RECENT_MAX = 6

    private val _state = MutableStateFlow(Diagnostics())
    val state: StateFlow<Diagnostics> = _state.asStateFlow()

    private var working = Diagnostics()
    private var lastPublish = 0L

    @Synchronized
    fun setLiveScan(text: String) {
        working = working.copy(liveScan = text)
        publish(force = true)
    }

    @Synchronized
    fun onPacket() {
        working = working.copy(anyPackets = working.anyPackets + 1)
        publish()
    }

    @Synchronized
    fun onApplePacket(data: ByteArray) {
        val type = if (data.isNotEmpty()) data[0].toInt() and 0xFF else -1
        working = working.copy(
            applePackets = working.applePackets + 1,
            appleTypes = if (type in working.appleTypes) working.appleTypes else working.appleTypes + type,
        )
    }

    @Synchronized
    fun onPodPacket(data: ByteArray, rssi: Int, decoded: String?) {
        val packet = RawPacket(
            hex = data.take(12).joinToString(" ") { "%02X".format(it) },
            rssi = rssi,
            decoded = decoded ?: "not understood",
        )
        working = working.copy(
            podPackets = working.podPackets + 1,
            recent = (listOf(packet) + working.recent).take(RECENT_MAX),
            lastPodParsed = decoded != null,
        )
    }

    private fun publish(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPublish < PUBLISH_INTERVAL_MS) return
        lastPublish = now
        _state.value = working
    }
}
