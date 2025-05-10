package com.allenchu66.geofenceapp.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.allenchu66.geofenceapp.repository.SharedUserRepository

class SharedUserViewModelFactory(private val repository: SharedUserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SharedUserViewModel(repository) as T
    }
}
