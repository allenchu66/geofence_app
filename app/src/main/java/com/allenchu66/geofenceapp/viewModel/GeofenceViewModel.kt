package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.allenchu66.geofenceapp.GeofenceHelper
import com.allenchu66.geofenceapp.database.GeofenceDatabase
import com.allenchu66.geofenceapp.manager.GeofenceManager
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.allenchu66.geofenceapp.repository.GeofenceRepository
import com.allenchu66.geofenceapp.repository.LocationRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class GeofenceViewModel(
    application: Application) : AndroidViewModel(application) {

    private val TAG = "GeofenceViewModel"

    private val manager = GeofenceManager(
        context = application,
        localRepo = GeofenceLocalRepository(
            GeofenceDatabase.getInstance(application).geofenceDao()
        ),
        remoteRepo = GeofenceRepository()
    )

    private val _ownerGeofences = MutableLiveData<List<GeofenceData>>()
    //owner為別人設定的那些 Geofence。
    val ownerGeofences: LiveData<List<GeofenceData>> = _ownerGeofences

    //別人幫你（currentUser）設定的那些 Geofence。
    private val _incomingGeofences = MutableLiveData<List<GeofenceData>>()
    val incomingGeofences: LiveData<List<GeofenceData>> = _incomingGeofences

    private var incomingGeofenceListener: ListenerRegistration? = null

    fun loadGeofencesSetByMe(targetUid: String) {
        manager.loadGeofencesSetByMe(
            targetUid = targetUid,
            onSuccess = { list ->
                _ownerGeofences.postValue(list)
                Log.d(TAG, "Owner geofences loaded: ${list.size}")
            },
            onFailure = { exception ->
                Log.e(TAG, "Error fetching geofences set by me", exception)
            }
        )
    }

    fun observeIncomingGeofencesRealtime() {
        incomingGeofenceListener?.remove()

        incomingGeofenceListener = manager.observeIncomingGeofences(
            onUpdate = { list ->
                Log.d(TAG, "Realtime incoming geofences updated: ${list.size}")
                manager.removeAllLocalGeofences()
                list.forEach { manager.addGeofenceToLocal(it) }
                _incomingGeofences.postValue(list)
            },
            onError = { e ->
                Log.e(TAG, "Realtime geofence error", e)
            }
        )
    }

    //OwnerUid => 要觸發Geofence的Uid
    //TargetUid => 要接收通知的Uid
    fun uploadGeofence(
        fenceId: String?,
        ownerUid: String,
        latitude: Double,
        longitude: Double,
        radius: Float,
        locationName: String,
        transition: List<String>,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit) {

        manager.uploadGeofence(
            fenceId = fenceId,
            ownerUid = ownerUid,
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            locationName = locationName,
            transition = transition,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    fun deleteGeofence(
        ownerUid: String,
        fenceId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit) {
        manager.deleteGeofence(
            ownerUid = ownerUid,
            fenceId = fenceId,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    override fun onCleared() {
        incomingGeofenceListener?.remove()
        super.onCleared()
    }

}