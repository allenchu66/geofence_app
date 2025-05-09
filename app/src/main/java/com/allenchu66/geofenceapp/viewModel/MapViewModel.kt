package com.allenchu66.geofenceapp.viewModel

import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.repository.LocationRepository

class MapViewModel(private val repo: LocationRepository) : ViewModel() {
    fun updateLocationToFirestore(userId: String, lat: Double, lng: Double) {
        repo.uploadLocation(userId, lat, lng)
    }
}
