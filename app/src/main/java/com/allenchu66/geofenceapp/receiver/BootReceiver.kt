package com.allenchu66.geofenceapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.allenchu66.geofenceapp.service.LocationUpdateService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val svcIntent = Intent(context, LocationUpdateService::class.java)
            ContextCompat.startForegroundService(context, svcIntent)
        }
    }
}