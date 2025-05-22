package com.allenchu66.geofenceapp.viewModel

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allenchu66.geofenceapp.model.LocationCluster
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

class MapViewModel(application: Application, private val repo: LocationRepository) :  AndroidViewModel(application)  {
    private val _sharedLocations = MutableLiveData<List<SharedLocation>>()
    val sharedLocations: LiveData<List<SharedLocation>> = _sharedLocations

    private val _historyLocations = MutableLiveData<List<LocationCluster>>()
    val historyLocations: LiveData<List<LocationCluster>> = _historyLocations

    private val _historyWithAddress = MutableLiveData<List<LocationCluster>>()
    val historyWithAddress: LiveData<List<LocationCluster>> = _historyWithAddress

    private val geocoder = Geocoder(application, Locale.getDefault())

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
            Log.d("20250518","rawList size:"+list.size.toString())
            _historyLocations.postValue(list)
            fetchAddressesForClusters(list)
        }
    }

    /**
     * 呼叫這個把 lat/lng 轉成字串地址
     * 一定要在協程 + IO 線程執行
     */
    fun fetchAddressesForClusters(clusters: List<LocationCluster>) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = clusters.map { cluster ->
                val address = try {
                    geocoder.getFromLocation(
                        cluster.latLng.latitude,
                        cluster.latLng.longitude,
                        1
                    )?.firstOrNull()?.getAddressLine(0) ?: "Unknown"
                } catch (e: IOException) {
                    "Geocoder error"
                }
                cluster.copy(locationName = address)
            }
            _historyWithAddress.postValue(updated)
        }
    }
}
