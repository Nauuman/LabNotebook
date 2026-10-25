package com.personal.podpulse.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun push(context: Context) {
        runCatching { PodWidget().updateAll(context) }
    }
}
