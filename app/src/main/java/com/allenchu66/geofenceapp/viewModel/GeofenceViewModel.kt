package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.allenchu66.geofenceapp.GeofenceHelper
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.allenchu66.geofenceapp.repository.GeofenceRepository
import com.allenchu66.geofenceapp.repository.LocationRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore

class GeofenceViewModel(
    application: Application,
    private val localRepo: GeofenceLocalRepository,
    private val remoRepository: GeofenceRepository) : AndroidViewModel(application) {

    private val helper = GeofenceHelper(application,localRepo)
    private val firestore = Firebase.firestore

    private val _ownerGeofences = MutableLiveData<List<GeofenceData>>()
    //owner為別人設定的那些 Geofence。
    val ownerGeofences: LiveData<List<GeofenceData>> = _ownerGeofences

    //別人幫你（currentUser）設定的那些 Geofence。
    private val _incomingGeofences = MutableLiveData<List<GeofenceData>>()
    val incomingGeofences: LiveData<List<GeofenceData>> = _incomingGeofences

    fun loadOwnerGeofencesForTarget(targetUid: String) {
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: return
        remoRepository.getOwnerGeofences(owner, targetUid)
            .addOnSuccessListener { snap ->
                val list = snap.documents
                    .mapNotNull { it.toObject(GeofenceData::class.java) }
                _ownerGeofences.postValue(list)
            }
    }

    // 讀取並顯示 owner 設定的所有 Geofence
    fun loadOwnerGeofences() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        remoRepository.getOwnerGeofences(uid)
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
                _ownerGeofences.postValue(list)
            }
    }

    // 讀取並註冊所有針對 currentUser 的 Geofence
    fun loadIncomingGeofences() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d("Geofence","test1")
        helper.removeAllGeofences()
        remoRepository.getIncomingGeofences(uid)
            .addOnSuccessListener { snapshot ->
                Log.d("Geofence","test2")
                val list = snapshot.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
                _incomingGeofences.postValue(list)
                // 在本機註冊
                list.forEach { helper.addGeofence(it) }
            }.addOnFailureListener { e ->
                Log.e("Geofence","test2: failed to load incoming geofences", e)
            }
            .addOnCompleteListener {
                Log.d("Geofence","test2: complete, success=${it.isSuccessful}")
            }
    }

    fun uploadGeofence(targetUid: String, lat: Double, lng: Double, radius: Float, name: String, transition: List<String>) {
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = firestore.collection("users")
            .document(owner)
            .collection("geofences")
            .document()
        val fenceId = docRef.id
        val entry = GeofenceData(
            fenceId = fenceId,
            ownerUid = owner,
            targetUid = targetUid,
            latitude = lat,
            longitude = lng,
            radius = radius,
            locationName = name,
            transition = transition,
            createdAt = null,
            updatedAt = null
        )
        remoRepository.saveGeofence(entry)
    }

    // Owner 移除 Geofence
    fun removeFence(fenceId: String) {
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: return
        remoRepository.deleteGeofence(owner, fenceId)
            .addOnSuccessListener {
                helper.removeGeofence(fenceId)
                loadOwnerGeofences()
            }
    }
}