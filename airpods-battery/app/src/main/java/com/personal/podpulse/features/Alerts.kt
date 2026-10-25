package com.personal.podpulse.features

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.podpulse.MainActivity
import com.personal.podpulse.R
import com.personal.podpulse.data.AppSettings
import com.personal.podpulse.data.PodStatus

object Alerts {
    private const val CHANNEL_LIVE = "live"
    private const val CHANNEL_ALERTS = "alerts"
    const val ID_LIVE = 1
    private const val ID_LOW_BATTERY = 2
    private const val ID_CONNECTED = 3

    private const val LOW_THRESHOLD = 20
    private const val RESET_THRESHOLD = 40

    fun ensureChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_LIVE, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Battery while connected")
                    .setDescription("Ongoing AirPods battery while they're connected")
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Battery alerts")
                    .setDescription("Low battery and connection pop-ups")
                    .build(),
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** "L 80% · R 70% · Case 50%", with ⚡ on charging parts. */
    fun summary(s: PodStatus?): String? {
        if (s == null) return null
        fun part(label: String, level: Int?, charging: Boolean) =
            level?.let { "$label ${if (charging) "⚡" else ""}$it%" }
        val parts = if (s.model.single) {
            listOfNotNull(part("Battery", s.left, s.leftCharging))
        } else {
            listOfNotNull(
                part("L", s.left, s.leftCharging),
                part("R", s.right, s.rightCharging),
                part("Case", s.case, s.caseCharging),
            )
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun liveNotification(context: Context, s: PodStatus?): Notification =
        NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s?.model?.displayName ?: "AirPods connected")
            .setContentText(summary(s) ?: "Waiting for battery…")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

    @SuppressLint("MissingPermission") // canNotify() checks it
    private fun post(context: Context, id: Int, notification: Notification) {
        if (!canNotify(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun updateLive(context: Context, s: PodStatus?) = post(context, ID_LIVE, liveNotification(context, s))

    /** Fallback for the case pop-up when "display over other apps" isn't allowed. */
    fun showConnected(context: Context, s: PodStatus) {
        ensureChannels(context)
        post(
            context,
            ID_CONNECTED,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(s.model.displayName)
                .setContentText(summary(s))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .setTimeoutAfter(8_000)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    /** Notifies once when a bud drops to 20 %, re-arming after it's charged. */
    fun checkLowBattery(context: Context, s: PodStatus) {
        if (!AppSettings.flag(context, AppSettings.LOW_BATTERY)) return
        val buds = if (s.model.single) {
            listOf(Triple("Battery", s.left, s.leftCharging))
        } else {
            listOf(Triple("Left bud", s.left, s.leftCharging), Triple("Right bud", s.right, s.rightCharging))
        }
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val alerted = prefs.getBoolean("low_alerted", false)
        val low = buds.filter { (_, level, charging) -> level != null && level <= LOW_THRESHOLD && !charging }

        if (low.isNotEmpty() && !alerted) {
            ensureChannels(context)
            val lowest = low.minOf { it.second!! }
            val names = low.joinToString(" and ") { it.first.lowercase() }
            post(
                context,
                ID_LOW_BATTERY,
                NotificationCompat.Builder(context, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("AirPods battery low: $lowest%")
                    .setContentText(
                        if (s.model.single) "Charge them soon." else "Time to put the $names in the case.",
                    )
                    .setContentIntent(openApp(context))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build(),
            )
            prefs.edit().putBoolean("low_alerted", true).apply()
        } else if (alerted && buds.all { (_, level, charging) -> charging || level == null || level >= RESET_THRESHOLD }) {
            prefs.edit().putBoolean("low_alerted", false).apply()
        }
    }
}
