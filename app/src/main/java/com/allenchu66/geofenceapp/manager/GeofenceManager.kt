package com.allenchu66.geofenceapp.manager

import android.content.Context
import android.util.Log
import com.allenchu66.geofenceapp.GeofenceHelper
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.allenchu66.geofenceapp.repository.GeofenceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * GeofenceManager:
 * 抽取共用地理圍籬邏輯，供 Service 與 ViewModel 使用。
 */
class GeofenceManager(
    private val context: Context,
    private val localRepo: GeofenceLocalRepository,
    private val remoteRepo: GeofenceRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "GeofenceManager"
    private val helper = GeofenceHelper(context, localRepo)

    /**
     * 重新載入所有針對 currentUser 的 geofence:
     * 1. 清空本機註冊
     * 2. 從 Firestore 拉取並註冊
     */
    fun reloadAllGeofences(onComplete: (List<GeofenceData>) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "reloadAllGeofences: no authenticated user")
            return
        }
        helper.removeAllGeofences()
        Log.d(TAG, "reloadAllGeofences: 移除所有geofence")
        remoteRepo.getIncomingGeofences(uid)
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
                list.forEach { helper.addGeofence(it) }
                Log.d(TAG, "reloadAllGeofences uid: ${uid}")
                Log.d(TAG, "Geofences reloaded: ${list.size}")
                onComplete(list)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to reload geofences", e)
            }
    }

    /**
     * 讀取並回傳 "owner 為自己、target 為指定人" 的 geofence 列表
     */
    fun loadGeofencesSetByMe(
        targetUid: String,
        onSuccess: (List<GeofenceData>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val ownerUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "loadGeofencesSetByMe: no authenticated user")
            return
        }
        Log.d(TAG, "loadGeofencesSetByMe: 找尋${ownerUid}幫${targetUid}設定的geofence")
        remoteRepo.getGeofencesSetByMe(needNotifyUid = ownerUid, needDetectUid = targetUid)
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
                onSuccess(list)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    /**
     * 上傳新的 geofence
     */
    fun uploadGeofence(
        fenceId: String?,
        ownerUid: String,
        latitude: Double,
        longitude: Double,
        radius: Float,
        locationName: String,
        transition: List<String>,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        remoteRepo.saveGeofence(
            GeofenceData(
                fenceId = fenceId ?: firestore.collection("users").document(ownerUid)
                    .collection("geofences").document().id,
                ownerUid = ownerUid,
                targetUid = FirebaseAuth.getInstance().currentUser?.uid ?: return,
                latitude = latitude,
                longitude = longitude,
                radius = radius,
                locationName = locationName,
                transition = transition,
                createdAt = null,
                updatedAt = null
            )
        )
            .addOnSuccessListener {
                onSuccess(fenceId ?: it.toString())
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Unknown error")
            }
    }

    /**
     * 刪除指定 geofence
     */
    fun deleteGeofence(
        ownerUid: String,
        fenceId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        remoteRepo.deleteGeofence(ownerUid, fenceId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Unknown error") }
    }

    /**
     * 移除本機所有 geofence
     */
    fun removeAllLocalGeofences() {
        helper.removeAllGeofences()
    }
}
