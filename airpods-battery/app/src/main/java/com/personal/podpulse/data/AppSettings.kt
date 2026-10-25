package com.personal.podpulse.data

import android.content.Context

object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_BACKGROUND = "background_updates"
    private const val KEY_DEVICE_ADDRESS = "device_address"
    private const val KEY_DEVICE_NAME = "device_name"

    // Feature switches, all on by default.
    const val AUTO_PAUSE = "auto_pause"
    const val CASE_POPUP = "case_popup"
    const val LOW_BATTERY = "low_battery_alert"
    const val REMEMBER_LOCATION = "remember_location"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun backgroundEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BACKGROUND, true)

    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND, enabled).apply()
    }

    fun flag(context: Context, key: String): Boolean = prefs(context).getBoolean(key, true)

    fun setFlag(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(key, enabled).apply()
    }

    /** Bluetooth address of the user's own AirPods, used to recognise connect/disconnect. */
    fun deviceAddress(context: Context): String? = prefs(context).getString(KEY_DEVICE_ADDRESS, null)

    fun deviceName(context: Context): String? = prefs(context).getString(KEY_DEVICE_NAME, null)

    fun setDevice(context: Context, address: String, name: String) {
        prefs(context).edit()
            .putString(KEY_DEVICE_ADDRESS, address)
            .putString(KEY_DEVICE_NAME, name)
            .apply()
    }
}
