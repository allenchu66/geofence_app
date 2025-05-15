package com.allenchu66.geofenceapp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceHelper(private val context: Context,private val repo: GeofenceLocalRepository) {
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(entry: GeofenceData) {
        // 1. 轉成 flag
        val transitionTypes = entry.transition.fold(0) { acc, t ->
            acc or when (t.lowercase()) {
                "enter" -> Geofence.GEOFENCE_TRANSITION_ENTER
                "exit"  -> Geofence.GEOFENCE_TRANSITION_EXIT
                else    -> 0
            }
        }

        // 2. 建 Geofence
        val geofence = Geofence.Builder()
            .setRequestId(entry.fenceId)
            .setCircularRegion(entry.latitude, entry.longitude, entry.radius)
            .setTransitionTypes(transitionTypes)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        // 3. 建 request（同時檢查 enter & exit）
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
               0
            )
            .addGeofence(geofence)
            .build()

        // 4. 註冊
        geofencingClient.addGeofences(request, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("GeofenceHelper", "Added fence ${entry.fenceId}")
                CoroutineScope(Dispatchers.IO).launch { repo.save(entry) }
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceHelper", "Failed to add fence: $e")
            }
    }


    fun removeAllGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
        CoroutineScope(Dispatchers.IO).launch {repo.deleteAll() }
    }

    fun removeGeofence(fenceId: String) {
        geofencingClient.removeGeofences(listOf(fenceId))
            .addOnSuccessListener { Log.d("GeofenceHelper", "Removed fence $fenceId") }
            .addOnFailureListener { e -> Log.e("GeofenceHelper", "Failed to remove fence: $e") }
    }
}
