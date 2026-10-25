package com.personal.podpulse.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.personal.podpulse.ble.PodScanner
import com.personal.podpulse.data.AppSettings

class PodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // The periodic widget update doubles as a watchdog for the background scan.
        if (AppSettings.backgroundEnabled(context)) PodScanner.armBackground(context)
    }
}
