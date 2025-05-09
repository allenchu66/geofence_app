package com.allenchu66.geofenceapp.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class LocationUpdateService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val channelName = "Location Updates"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Tracking location")
            .setContentText("Your location is being updated in background")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300_000L).build() //5 min

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                val data = hashMapOf(
                    "user_id" to uid,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "timestamp" to Date()
                )
                FirebaseFirestore.getInstance().collection("locations")
                    .document(uid)
                    .set(data)
                    .addOnSuccessListener { Log.d("LocationService", "Location uploaded") }
                    .addOnFailureListener { Log.e("LocationService", "Upload failed", it) }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}