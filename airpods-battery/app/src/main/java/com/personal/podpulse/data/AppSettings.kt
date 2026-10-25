package com.personal.podpulse.data

import android.content.Context

object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_BACKGROUND = "background_updates"

    fun backgroundEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BACKGROUND, true)

    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BACKGROUND, enabled)
            .apply()
    }
}
