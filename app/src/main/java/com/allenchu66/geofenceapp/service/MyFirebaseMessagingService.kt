package com.allenchu66.geofenceapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.allenchu66.geofenceapp.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 收到新的 FCM token
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: 把這個 token 上傳到 Firestore 或你自己的 server
        Log.d("FCM", "New token: $token")
    }

    // 收到推播（無論前台或背景）
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 如果是 notification payload
        remoteMessage.notification?.let {
            showNotification(it.title, it.body)
        }

        // 如果是 data payload
        if (remoteMessage.data.isNotEmpty()) {
            val data = remoteMessage.data
            val fenceId = data["fenceId"]
            val action  = data["action"]
            val name    = data["notifyName"]
            // TODO: 依據需求處理資料訊息（可更新 UI、寫入本機等等）
            showNotification("Geofence $action", "$name ($fenceId)")
        }
    }

    private fun showNotification(title: String?, body: String?) {
        Log.d("FCM", "ShowNotification: $title $body")
        val channelId = "geofence_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_default_avatar)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
