package com.allenchu66.geofenceapp.viewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.repository.LocationRepository
import com.google.android.gms.maps.model.LatLng

class MapViewModel(private val repo: LocationRepository) : ViewModel() {
    private val _sharedLocations = MutableLiveData<Map<String, LatLng>>()
    val sharedLocations: LiveData<Map<String, LatLng>> = _sharedLocations

    fun updateLocationToFirestore(userId: String, lat: Double, lng: Double) {
        repo.uploadLocation(userId, lat, lng)
    }

    fun loadSharedLocations(currentUserId: String) {
        repo.getSharedFriendUids(currentUserId) { friendUids ->

            repo.listenToFriendLocations(friendUids) { locationMap ->
                _sharedLocations.postValue(locationMap)
            }
        }
    }
}
