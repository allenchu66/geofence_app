package com.allenchu66.geofenceapp.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import com.allenchu66.geofenceapp.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class LocationUpdateService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val TAG = "LocationUpdateService"

    override fun onCreate() {
        super.onCreate()
    }

    private fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fgService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        return (fine || coarse) && fgService
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val channelName = "定位更新"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
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
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) return
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val locationRequest =
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 120_000L)
                    .build() //120s
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                    val now = System.currentTimeMillis().toString()
                    val data = hashMapOf(
                        "user_id" to uid,
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to Date()
                    )
                    FirebaseFirestore.getInstance()
                        .collection("locations")
                        .document(uid)
                        .collection("history")
                        .document(now)
                        .set(data)
                        .addOnSuccessListener { Log.d(TAG, "Location uploaded") }
                        .addOnFailureListener { Log.e(TAG, "Upload failed", it) }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing, cannot start updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 如果服務被系統終止，儘量自動重啟
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "缺少啟動前景服務的必要權限，無法啟動服務")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()
        startLocationUpdates()

        return START_STICKY
    }
}