package com.allenchu66.geofenceapp.viewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.model.LocationCluster
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.repository.LocationRepository

class MapViewModel(private val repo: LocationRepository) : ViewModel() {
    private val _sharedLocations = MutableLiveData<List<SharedLocation>>()
    val sharedLocations: LiveData<List<SharedLocation>> = _sharedLocations

    private val _historyLocations = MutableLiveData<List<LocationCluster>>()
    val historyLocations: LiveData<List<LocationCluster>> = _historyLocations

    fun updateLocationToFirestore(userId: String, lat: Double, lng: Double) {
        repo.uploadLocation(userId, lat, lng)
    }

    fun loadSharedLocations(currentUserId: String) {
        repo.listenToSharedLocations(currentUserId) { combinedList ->
            _sharedLocations.postValue(combinedList)
        }
    }

    /**
     * 查詢 targetUid 在指定日期的定位歷史
     *
     * @param targetUid 使用者 UID
     * @param year      西元年（例如 2025）
     * @param month     月份 1~12
     * @param day       日 1~31
     */
    fun loadHistoryLocations(targetUid: String, year: Int, month: Int, day: Int) {
        repo.getHistoryLocationsForDate(targetUid, year, month, day) { list ->
            _historyLocations.postValue(list)
        }
    }
}
