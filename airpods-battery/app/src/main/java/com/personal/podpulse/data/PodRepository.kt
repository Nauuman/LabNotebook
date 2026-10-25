package com.personal.podpulse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the latest reading in memory and persists it so the widget survives process death. */
object PodRepository {
    private const val PREFS = "pod_status"

    /** Ignore very weak signals: those are almost always someone else's AirPods. */
    private const val MIN_RSSI = -95

    /** After this long without hearing the current device, accept any device. */
    private const val SWITCH_AFTER_MS = 10_000L

    /** A new device must be at most this much weaker to replace the current one. */
    private const val RSSI_HYSTERESIS = 10

    private const val PERSIST_INTERVAL_MS = 30_000L
    private const val WIDGET_REFRESH_MS = 5 * 60_000L

    private val _status = MutableStateFlow<PodStatus?>(null)
    val status: StateFlow<PodStatus?> = _status.asStateFlow()

    private var loaded = false
    private var lastPersist = 0L
    private var lastWidgetPush = 0L

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        _status.value = load(context)
        loaded = true
    }

    fun snapshot(context: Context): PodStatus? {
        init(context)
        return _status.value
    }

    /**
     * Offers a freshly decoded advertisement. Returns true when the home screen
     * widget should be refreshed.
     */
    @Synchronized
    fun submit(context: Context, candidate: PodStatus): Boolean {
        init(context)
        if (candidate.rssi < MIN_RSSI) return false

        val current = _status.value
        val accept = current == null ||
            candidate.timestamp - current.timestamp > SWITCH_AFTER_MS ||
            candidate.rssi >= current.rssi - RSSI_HYSTERESIS
        if (!accept) return false

        val changed = current == null || !candidate.sameReadingAs(current)
        _status.value = candidate

        val now = candidate.timestamp
        if (changed || now - lastPersist > PERSIST_INTERVAL_MS) {
            save(context, candidate)
            lastPersist = now
        }
        val refreshWidget = changed || now - lastWidgetPush > WIDGET_REFRESH_MS
        if (refreshWidget) lastWidgetPush = now
        return refreshWidget
    }

    private fun save(context: Context, s: PodStatus) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("model", s.modelId)
            .putInt("left", s.left ?: -1)
            .putInt("right", s.right ?: -1)
            .putInt("case", s.case ?: -1)
            .putBoolean("leftCharging", s.leftCharging)
            .putBoolean("rightCharging", s.rightCharging)
            .putBoolean("caseCharging", s.caseCharging)
            .putBoolean("leftInEar", s.leftInEar)
            .putBoolean("rightInEar", s.rightInEar)
            .putInt("rssi", s.rssi)
            .putLong("timestamp", s.timestamp)
            .apply()
    }

    private fun load(context: Context): PodStatus? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("timestamp")) return null
        fun level(key: String) = p.getInt(key, -1).takeIf { it >= 0 }
        return PodStatus(
            modelId = p.getInt("model", -1),
            left = level("left"),
            right = level("right"),
            case = level("case"),
            leftCharging = p.getBoolean("leftCharging", false),
            rightCharging = p.getBoolean("rightCharging", false),
            caseCharging = p.getBoolean("caseCharging", false),
            leftInEar = p.getBoolean("leftInEar", false),
            rightInEar = p.getBoolean("rightInEar", false),
            rssi = p.getInt("rssi", 0),
            timestamp = p.getLong("timestamp", 0L),
        )
    }
}
