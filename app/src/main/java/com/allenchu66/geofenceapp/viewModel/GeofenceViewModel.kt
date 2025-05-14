package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import android.util.Log
import android.widget.Toast
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

    private val TAG = "GeofenceViewModel"

    private val helper = GeofenceHelper(application,localRepo)
    private val firestore = Firebase.firestore

    private val _ownerGeofences = MutableLiveData<List<GeofenceData>>()
    //owner為別人設定的那些 Geofence。
    val ownerGeofences: LiveData<List<GeofenceData>> = _ownerGeofences

    //別人幫你（currentUser）設定的那些 Geofence。
    private val _incomingGeofences = MutableLiveData<List<GeofenceData>>()
    val incomingGeofences: LiveData<List<GeofenceData>> = _incomingGeofences

    fun loadGeofencesSetByMe(targetUid: String) {
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d(TAG,targetUid+"   "+owner)
        remoRepository.getGeofencesSetByMe(needNotifyUid = owner, needDetectUid = targetUid)
            .addOnSuccessListener { snap ->
                val list = snap.documents
                    .mapNotNull { it.toObject(GeofenceData::class.java) }
                _ownerGeofences.postValue(list)
                Log.d(TAG,list.size.toString())
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error fetching geofences: ", e)
            }
    }

    // 讀取並註冊所有針對 currentUser 的 Geofence
    fun loadIncomingGeofences() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        helper.removeAllGeofences()
        remoRepository.getIncomingGeofences(uid)
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
                _incomingGeofences.postValue(list)
                // 在本機註冊
                list.forEach { helper.addGeofence(it) }
            }.addOnFailureListener { e ->
                Log.e(TAG,"failed to load incoming geofences", e)
            }
            .addOnCompleteListener {
                Log.d(TAG,"test2: complete, success=${it.isSuccessful}")
            }
    }

    //OwnerUid => 要觸發Geofence的Uid
    //TargetUid => 要接收通知的Uid
    fun uploadGeofence(
        fenceId: String?,
        ownerUid: String,
        lat: Double,
        lng: Double,
        radius: Float,
        name: String,
        transition: List<String>,
        onSuccess: (fenceId: String) -> Unit,
        onFailure: (String) -> Unit) {
        val targetUid = FirebaseAuth.getInstance().currentUser?.uid ?: return //發送通知的目標
        val docRef = firestore.collection("users")
            .document(ownerUid)
            .collection("geofences")
            .document()

        val finalFenceId = fenceId ?: docRef.id

        val entry = GeofenceData(
            fenceId = finalFenceId,
            ownerUid = ownerUid,
            targetUid = targetUid,
            latitude = lat,
            longitude = lng,
            radius = radius,
            locationName = name,
            transition = transition,
            createdAt = null,
            updatedAt = null
        )
        Log.d("uploadGeofence","要觸發Geofence 的UID ${ownerUid}")
        Log.d("uploadGeofence","要接收通知 的UID ${targetUid}")
        remoRepository.saveGeofence(entry).addOnSuccessListener {
            onSuccess(finalFenceId)
            //Toast.makeText(getApplication(), "Geofence新增成功", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            onFailure(e.message ?: "Unknown error")
            //Toast.makeText(getApplication(), "Geofence新增失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteGeofence(
        ownerUid: String,
        fenceId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit) {
        remoRepository.deleteGeofence(ownerUid, fenceId)
            .addOnSuccessListener {
               onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Unknown error")
            }
    }
}