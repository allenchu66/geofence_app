package com.allenchu66.geofenceapp.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp


data class LocationCluster(
    val latLng: LatLng,
    val startTs: Timestamp,    // 區塊開始時間
    val endTs: Timestamp,       // 區塊結束時間
    val locationName: String
)