package com.allenchu66.geofenceapp.repository

import android.util.Log
import android.widget.Toast
import com.allenchu66.geofenceapp.model.GeofenceData
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class GeofenceRepository {
    private val db = Firebase.firestore

    fun getGeofencesSetByMe(needNotifyUid: String, needDetectUid: String) =
        db.collection("users")
            .document(needDetectUid)
            .collection("geofences")
            .whereEqualTo("targetUid", needNotifyUid)
            .get()

    // 保存到 /users/{ownerUid}/geofences/{fenceId}
    //ownerUid 是指要設定geofence的uid
    //targetUid 始指要接收geofence通知的uid
    fun saveGeofence(entry: GeofenceData): com.google.android.gms.tasks.Task<Void> {
        val path = db.collection("users")
            .document(entry.ownerUid)
            .collection("geofences")
            .document(entry.fenceId)
        val task = path.set(entry)

        task.addOnSuccessListener {
            Log.d("Firestore", "Geofence saved successfully: ${entry.fenceId}")
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error saving geofence ${entry.fenceId}", e)
        }

        return task
    }

    fun deleteGeofence(ownerUid: String, fenceId: String): com.google.android.gms.tasks.Task<Void> {
        val task = db.collection("users")
            .document(ownerUid)
            .collection("geofences")
            .document(fenceId)
            .delete()
        task.addOnSuccessListener {
            Log.d("Firestore", "deleteGeofence successfully: ${fenceId}")
        }.addOnFailureListener { e ->
            Log.e("Firestore", "deleteGeofence geofence ${fenceId}", e)
        }
        return task
    }

    // target 端讀他是 targetUid 相關的 fences
    fun getIncomingGeofences(MyUid: String) = db.collection("users")
        .document(MyUid)
        .collection("geofences")
        .get()

    fun listenIncomingGeofences(
        myUid: String,
        onUpdate: (List<GeofenceData>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("users")
            .document(myUid)
            .collection("geofences")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { it.toObject(GeofenceData::class.java) } ?: emptyList()
                onUpdate(list)
            }
    }
}



