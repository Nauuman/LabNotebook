package com.personal.podpulse.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the scanner has actually seen, shown in the app to troubleshoot "Searching…". */
data class Diagnostics(
    val liveScan: String = "Not started",
    val anyPackets: Int = 0,
    val applePackets: Int = 0,
    val podPackets: Int = 0,
    val appleTypes: Set<Int> = emptySet(),
    val lastPodHex: String? = null,
    val lastPodRssi: Int? = null,
    val lastPodParsed: Boolean? = null,
)

object ScanDiagnostics {
    private val _state = MutableStateFlow(Diagnostics())
    val state: StateFlow<Diagnostics> = _state.asStateFlow()

    fun setLiveScan(text: String) = _state.update { it.copy(liveScan = text) }

    fun onPacket() = _state.update { it.copy(anyPackets = it.anyPackets + 1) }

    fun onApplePacket(data: ByteArray) = _state.update {
        val type = if (data.isNotEmpty()) data[0].toInt() and 0xFF else -1
        it.copy(applePackets = it.applePackets + 1, appleTypes = it.appleTypes + type)
    }

    fun onPodPacket(data: ByteArray, rssi: Int, parsed: Boolean) = _state.update {
        it.copy(
            podPackets = it.podPackets + 1,
            lastPodHex = data.joinToString(" ") { b -> "%02X".format(b) },
            lastPodRssi = rssi,
            lastPodParsed = parsed,
        )
    }
}
