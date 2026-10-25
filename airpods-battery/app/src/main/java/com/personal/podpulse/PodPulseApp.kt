package com.personal.podpulse

import android.app.Application
import android.content.Context
import com.personal.podpulse.data.LastSeen
import com.personal.podpulse.data.PodRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PodPulseApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        PodRepository.init(this)
        LastSeen.init(this)
    }

    companion object {
        fun scope(context: Context): CoroutineScope =
            (context.applicationContext as PodPulseApp).appScope
    }
}
