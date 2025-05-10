package com.allenchu66.geofenceapp.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.model.SharedUser
import com.allenchu66.geofenceapp.repository.SharedUserRepository

class SharedUserViewModel(private val repository: SharedUserRepository) : ViewModel() {

    private val _sharedUsers = MutableLiveData<List<SharedUser>>()
    val sharedUsers: LiveData<List<SharedUser>> = _sharedUsers

    private val _shareResult = MutableLiveData<Pair<Boolean, String>>()
    val shareResult: LiveData<Pair<Boolean, String>> get() = _shareResult

    fun loadSharedUsers() {
        repository.getSharedUsers { users ->
            _sharedUsers.value = users
        }
    }

    fun updateShareStatus(email: String, status: String) {
        repository.updateShareStatusByEmail(email, status){
            loadSharedUsers()
        }
    }

    fun sendShareRequest(email: String) {
        repository.sendShareRequestByEmail(email) { success, message ->
            _shareResult.postValue(Pair(success, message))
        }
    }
}
