package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.repository.FirebaseAuthRepository

class LoginViewModel(private val authRepository: FirebaseAuthRepository):ViewModel() {
    private val _loginSuccess = MutableLiveData<Boolean>()
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    fun login(email: String, password: String) {
        authRepository.login(email, password)
            .addOnCompleteListener { task ->
                _loginSuccess.value = task.isSuccessful
            }
    }
}