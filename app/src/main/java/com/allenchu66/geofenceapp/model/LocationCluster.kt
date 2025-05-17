package com.allenchu66.geofenceapp.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp


data class LocationCluster(
    val latLng: LatLng,           // 代表位置座標（可用第一筆或平均）
    val startTs: Timestamp,    // 區塊開始時間
    val endTs: Timestamp       // 區塊結束時間
)