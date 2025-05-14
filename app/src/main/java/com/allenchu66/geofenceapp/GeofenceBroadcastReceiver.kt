package com.allenchu66.geofenceapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.allenchu66.geofenceapp.database.GeofenceDatabase
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "GeofenceReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        GeofencingEvent.fromIntent(intent)?.let { event ->
            if (event.hasError()) {
                Log.e(TAG, "Error code: ${event.errorCode}")
                return
            }
            val transition = when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                Geofence.GEOFENCE_TRANSITION_EXIT  -> "exit"
                else                                -> return
            }

            val db   = Room.databaseBuilder(
                context.applicationContext,
               GeofenceDatabase::class.java,
                "app_db"
            ).build()
            val repo = GeofenceLocalRepository(db.geofenceDao())

            event.triggeringGeofences?.forEach { geofence ->
                val fenceId = geofence.requestId
                CoroutineScope(Dispatchers.IO).launch {
                    // 從 Room 拿完整設定
                    val entry = repo.get(fenceId) ?: return@launch

                    // 3a. 本地通知（保留）
                    //notifyLocal(context, fenceId, transition)

                    Log.d(TAG,"ownerUid:${entry.ownerUid}")
                    Log.d(TAG,"targetUid:${entry.targetUid}")

                    // 3b. 上報到 Firestore，使用 entry.ownerUid、entry.targetUid
                    reportEventToFirestore(
                        ownerUid   = entry.ownerUid,
                        targetUid = entry.targetUid,
                        fenceId    = fenceId,
                        locationName       = entry.locationName,
                        action     = transition
                    )
                }
            }
        }
    }


    /**
     * ownerUid : 偵測Geofence的人
     * targetUid : 要被發送通知的人
     * */
    private fun reportEventToFirestore(
        ownerUid: String,
        targetUid: String,
        fenceId:   String,
        locationName: String,
        action:    String
    ) {
        val db = Firebase.firestore
        db.collection("geofence_events")
            .document(ownerUid)
            .collection("events")
            .add(mapOf(
                "targetUid" to targetUid,
                "fenceId"    to fenceId,
                "locationName" to locationName,
                "action"     to action,
                "timestamp"  to FieldValue.serverTimestamp()
            ))
            .addOnSuccessListener { Log.d("GeofenceReceiver", "上拋成功") }
            .addOnFailureListener { e -> Log.e("GeofenceReceiver", "上拋失敗", e) }
    }

    private fun notifyLocal(context: Context, fenceId: String, action: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "geofence_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Geofence Alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Geofence $action")
            .setContentText("Fence ID: $fenceId")
            .setSmallIcon(R.drawable.ic_default_avatar)
            .build()
        nm.notify(fenceId.hashCode(), notif)
    }
}
