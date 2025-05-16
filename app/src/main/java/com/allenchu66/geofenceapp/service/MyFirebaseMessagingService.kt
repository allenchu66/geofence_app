package com.allenchu66.geofenceapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.room.Room
import com.allenchu66.geofenceapp.GeofenceHelper
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.database.GeofenceDatabase
import com.allenchu66.geofenceapp.manager.GeofenceManager
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.allenchu66.geofenceapp.repository.GeofenceRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 收到新的 FCM token
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
    }

    // 建立 GeofenceManager
    private val manager by lazy {
        GeofenceManager(
            context = this,
            localRepo = GeofenceLocalRepository(
                GeofenceDatabase.getInstance(applicationContext).geofenceDao()
            ),
            remoteRepo = GeofenceRepository(),
            firestore = Firebase.firestore
        )
    }

    // 收到推播（無論前台或背景）
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 如果是 notification payload
//        remoteMessage.notification?.let {
//            showNotification(it.title, it.body)
//        }

        // 如果是 data payload
        if (remoteMessage.data.isNotEmpty()) {
            val data = remoteMessage.data
            when (data["type"]){
                "GEOFENCE_CHANGE" -> {
                    // 重新 load 所有 geofence
                    Log.d("FCM", "GEOFENCE_CHANGE received, reloading geofences…")
                    manager.reloadAllGeofences { list ->
                        Log.d("FCM", "Geofences reloaded: ${'$'}{list.size}")
                    }
                }
                "GEOFENCE_EVENT" -> {
                    // 實際顯示進/出通知
                    val title    = data["notifyTitle"]
                    val content  = data["notifyContent"]
                    val photoUri = data["photoUri"]
                    showNotification(title, content, photoUri)
                }
                else -> {
                    Log.w("FCM", "Unknown message type: ${data["type"]}")
                }
            }
        }
    }

    private fun showNotification(title: String?,content: String?, photoUri: String?) {
        Log.d("FCM", "ShowNotification: $title $content")
        val channelId = "geofence_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "地理圍欄偵測",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Geofence 觸發通知"
            //enableLights(true)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)

        if (photoUri == null) {
            val notif = NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_geofence_icon)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notif)
            return
        }

        // 若 photoUri 有值，就載入圖並顯示為大圖
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(photoUri)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(input)

                val notif = NotificationCompat.Builder(this@MyFirebaseMessagingService, channelId)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_geofence_icon)
                    .setLargeIcon(bitmap)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .build()
                nm.notify(System.currentTimeMillis().toInt(), notif)
            } catch (e: Exception) {
                Log.e("FCM", "Failed to load image: ${e.message}")
            }
        }
    }

    private val db = Firebase.firestore
    private val roomDB by lazy { GeofenceDatabase.getInstance(applicationContext) }
    private val geofenceLocalRepo by lazy {
        GeofenceLocalRepository(roomDB.geofenceDao())
    }
    private fun reloadGeofences() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // 建立你的 repo + helper
        val helper = GeofenceHelper(this, geofenceLocalRepo)

        // 先移除所有本機 geofence
        helper.removeAllGeofences()


        db.collection("users")
            .document(uid)
            .collection("geofences")
            .get().addOnSuccessListener { snap ->
                val list = snap.documents
                    .mapNotNull { it.toObject(GeofenceData::class.java) }
                list.forEach { helper.addGeofence(it) }
                Log.d("FCM", "Geofences reloaded: ${list.size}")
            }
            .addOnFailureListener {
                Log.e("FCM", "Failed to reload geofences", it)
            }
    }


}
