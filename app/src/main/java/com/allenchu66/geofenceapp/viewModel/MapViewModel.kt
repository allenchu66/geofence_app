package com.allenchu66.geofenceapp.viewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.repository.LocationRepository

class MapViewModel(private val repo: LocationRepository) : ViewModel() {
    private val _sharedLocations = MutableLiveData<List<SharedLocation>>()
    val sharedLocations: LiveData<List<SharedLocation>> = _sharedLocations

    fun updateLocationToFirestore(userId: String, lat: Double, lng: Double) {
        repo.uploadLocation(userId, lat, lng)
    }

    fun loadSharedLocations(currentUserId: String) {
        repo.listenToSharedLocations(currentUserId) { combinedList ->
            _sharedLocations.postValue(combinedList)
        }
    }
}
