package com.allenchu66.geofenceapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GeofencingEvent.fromIntent(intent)?.let { event ->
            if (event.hasError()) {
                Log.e("GeofenceReceiver", "Error code: ${event.errorCode}")
                return
            }
            val transition = event.geofenceTransition
            val fenceIds = event.triggeringGeofences?.map { it.requestId }
            if (fenceIds != null) {
                fenceIds.forEach { id ->
                    when (transition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> notify(context, id, "entered")
                        Geofence.GEOFENCE_TRANSITION_EXIT  -> notify(context, id, "exited")
                    }
                }
            }
        }
    }

    private fun notify(context: Context, fenceId: String, action: String) {
        Log.e("GeofenceReceiver", "action: ${action}")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "geofence_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Geofence $action")
            .setContentText("Fence ID: $fenceId")
            .setSmallIcon(R.drawable.ic_default_avatar)
            .build()
        nm.notify(fenceId.hashCode(), notif)
    }
}
