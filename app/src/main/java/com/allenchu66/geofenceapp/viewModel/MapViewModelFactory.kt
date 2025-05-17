package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.allenchu66.geofenceapp.repository.LocationRepository

class MapViewModelFactory(
    private val app: Application,
    private val repository: LocationRepository
) : ViewModelProvider.AndroidViewModelFactory(app) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(app,repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}