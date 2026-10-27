package com.personal.podpulse

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.util.Date

/** Keeps the last crash on disk so it can be shown in the app and sent for fixing. */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "crash_log"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(app.filesDir, FILE).writeText(
                    "${Date()}\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
                        "Thread: ${thread.name}\n\n" + error.stackTraceToString().take(8_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
        recordAnr(app)
    }

    /** ANRs (frozen app) don't go through the crash handler; Android 11+ records them. */
    private fun recordAnr(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = context.getSystemService(ActivityManager::class.java) ?: return
            val exit = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
            if (exit.reason != ApplicationExitInfo.REASON_ANR) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong("anr_seen", 0L) == exit.timestamp) return
            prefs.edit().putLong("anr_seen", exit.timestamp).apply()
            File(context.filesDir, FILE).writeText(
                "${Date(exit.timestamp)}\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n\n" +
                    "App froze (ANR): ${exit.description}",
            )
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
