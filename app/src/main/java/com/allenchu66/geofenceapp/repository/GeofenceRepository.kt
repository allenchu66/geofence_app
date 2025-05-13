package com.allenchu66.geofenceapp.repository

import com.allenchu66.geofenceapp.model.GeofenceData
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore

class GeofenceRepository {
    private val db = Firebase.firestore
    private fun currentUid() = FirebaseAuth.getInstance().currentUser!!.uid

    fun getOwnerGeofences(ownerUid: String, targetUid: String) =
        db.collection("users")
            .document(ownerUid)
            .collection("geofences")
            .whereEqualTo("targetUid", targetUid)
            .get()

    // 保存到 /users/{ownerUid}/geofences/{fenceId}
    fun saveGeofence(entry: GeofenceData): com.google.android.gms.tasks.Task<Void> {
        val path = db.collection("users")
            .document(entry.ownerUid)
            .collection("geofences")
            .document(entry.fenceId)
        return path.set(entry)
    }

    fun deleteGeofence(ownerUid: String, fenceId: String): com.google.android.gms.tasks.Task<Void> {
        return db.collection("users")
            .document(ownerUid)
            .collection("geofences")
            .document(fenceId)
            .delete()
    }

    // 取得 owner 自己的所有
    fun getOwnerGeofences(ownerUid: String) = db.collection("users")
        .document(ownerUid)
        .collection("geofences")
        .get()

    // target 端讀他是 targetUid 相關的 fences
    fun getIncomingGeofences(targetUid: String) = db.collectionGroup("geofences")
        .whereEqualTo("targetUid", targetUid)
        .get()
}



