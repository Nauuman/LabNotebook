package com.nauuman.measure.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** User preferences backed by SharedPreferences and observable from Compose. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun defaultImperial(): Boolean =
        Locale.getDefault().country in setOf("US", "LR", "MM")

    var imperial by mutableStateOf(prefs.getBoolean(KEY_IMPERIAL, defaultImperial()))
        private set
    var showPlanes by mutableStateOf(prefs.getBoolean(KEY_PLANES, true))
        private set
    var snapping by mutableStateOf(prefs.getBoolean(KEY_SNAP, true))
        private set
    var haptics by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set
    var saveToGallery by mutableStateOf(prefs.getBoolean(KEY_GALLERY, true))
        private set

    fun updateImperial(v: Boolean) { imperial = v; prefs.edit().putBoolean(KEY_IMPERIAL, v).apply() }
    fun updateShowPlanes(v: Boolean) { showPlanes = v; prefs.edit().putBoolean(KEY_PLANES, v).apply() }
    fun updateSnapping(v: Boolean) { snapping = v; prefs.edit().putBoolean(KEY_SNAP, v).apply() }
    fun updateHaptics(v: Boolean) { haptics = v; prefs.edit().putBoolean(KEY_HAPTICS, v).apply() }
    fun updateSaveToGallery(v: Boolean) { saveToGallery = v; prefs.edit().putBoolean(KEY_GALLERY, v).apply() }

    private companion object {
        const val KEY_IMPERIAL = "imperial"
        const val KEY_PLANES = "show_planes"
        const val KEY_SNAP = "snapping"
        const val KEY_HAPTICS = "haptics"
        const val KEY_GALLERY = "save_to_gallery"
    }
}
