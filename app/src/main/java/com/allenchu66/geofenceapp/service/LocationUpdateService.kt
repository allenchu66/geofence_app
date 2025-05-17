package com.allenchu66.geofenceapp.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.allenchu66.geofenceapp.R
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
        val channelName = "定位更新"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("更新定位")
            .setContentText("您的位置會在背景更新")
            .setSmallIcon(R.drawable.ic_geofence_icon)
            .setOngoing(true)       // → 防止使用者滑掉
            .setAutoCancel(false)   // → 不自動取消
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
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L).build() //30s
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
                    .collection("history")
                    .add(data)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 如果服務被系統終止，儘量自動重啟
        return START_STICKY
    }
}