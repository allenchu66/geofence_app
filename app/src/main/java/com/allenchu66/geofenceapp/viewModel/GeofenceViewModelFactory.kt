package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.allenchu66.geofenceapp.repository.GeofenceRepository

class GeofenceViewModelFactory(
    private val application: Application,
    private val localRepo : GeofenceLocalRepository,
    private val repository: GeofenceRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GeofenceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GeofenceViewModel(application, localRepo,repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
