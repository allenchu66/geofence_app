package com.allenchu66.geofenceapp.repository

import android.util.Log
import com.allenchu66.geofenceapp.model.LocationCluster
import com.allenchu66.geofenceapp.model.ShareRequest
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.model.SharedUser
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

class LocationRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun uploadLocation(userId: String, lat: Double, lng: Double) {
        val now = System.currentTimeMillis().toString()
        val locationData = hashMapOf(
            "user_id" to userId,
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("locations")
            .document(userId)
            .collection("history")
            .document(now)
            .set(locationData)
            .addOnSuccessListener { Log.d("LocationUpload", "Success") }
            .addOnFailureListener { e -> Log.e("LocationUpload", "Error", e) }
    }


    fun listenToSharedLocations(
        currentUserId: String,
        onUpdate: (List<SharedLocation>) -> Unit
    ) {
        val userMap = mutableMapOf<String, SharedUser>()
        val locationMap = mutableMapOf<String, LatLng>()
        val timestampMap = mutableMapOf<String, Timestamp?>()

        firestore.collection("share_requests")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val acceptedUids = snap.documents.mapNotNull { doc ->
                    doc.toObject(ShareRequest::class.java)
                        ?.takeIf { it.status == "accepted" }
                        ?.otherUid(currentUserId)
                }

                userMap.keys.retainAll(acceptedUids)
                locationMap.keys.retainAll(acceptedUids)
                timestampMap.keys.retainAll(acceptedUids)

                acceptedUids.forEach { uid ->
                    firestore.collection("users")
                        .document(uid)
                        .addSnapshotListener { userSnap, err ->
                            Log.d("Repo-UserSnap", "uid=$uid exists=${userSnap?.exists()} err=$err")
                            if (userSnap != null && userSnap.exists()) {
                                userSnap.toObject(SharedUser::class.java)?.let { user ->
                                    userMap[uid] = user
                                    emitSharedLocations(onUpdate, userMap, locationMap,timestampMap)
                                }
                            }
                        }
                    firestore.collection("locations")
                        .document(uid)
                        .collection("history")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .addSnapshotListener { historySnap, error ->
                            if (error != null || historySnap == null) return@addSnapshotListener

                            val doc = historySnap.documents.firstOrNull() ?: return@addSnapshotListener
                            val lat = doc.getDouble("latitude") ?: return@addSnapshotListener
                            val lng = doc.getDouble("longitude") ?: return@addSnapshotListener
                            val ts  = doc.getTimestamp("timestamp")

                            locationMap[uid] = LatLng(lat, lng)
                            timestampMap[uid] = ts

                            Log.d("Repo-Location", "for $uid -> $lat,$lng $timestampMap")
                            emitSharedLocations(onUpdate, userMap, locationMap, timestampMap)
                        }
                }
            }
    }

    private fun emitSharedLocations(
        onUpdate: (List<SharedLocation>) -> Unit,
        userMap: Map<String, SharedUser>,
        locationMap: Map<String, LatLng>,
        timestampMap: Map<String, Timestamp?>
    ) {
        val combined = userMap.mapNotNull { (uid, user) ->
            locationMap[uid]?.let { latLng ->
                SharedLocation(
                    user      = user,
                    uid       = uid,
                    latLng    = latLng,
                    timestamp = timestampMap[uid]
                )
            }
        }
        onUpdate(combined)
    }

    /**
     * 取得某個 userId 在指定日期的定位歷史
     *
     * @param userId     目標使用者 UID
     * @param year       西元年（例如 2025）
     * @param month      月份 1~12
     * @param day        日期 1~31
     * @param onResult   查詢到的 SharedLocation 列表會回傳到這個 callback
     */
    fun getHistoryLocationsForDate(
        userId: String,
        year: Int,
        month: Int,
        day: Int,
        onResult: (List<LocationCluster>) -> Unit
    ) {
        Log.d("20250517","${month} - ${day}")
        // 1. 計算當天 00:00:00 的時間戳
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = cal.time

        // 2. 計算當天 23:59:59 的時間戳
        cal.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endDate = cal.time

        // 3. 先抓 SharedUser 資訊
        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userSnap ->
                val sharedUser = userSnap.toObject(SharedUser::class.java)
                if (sharedUser == null) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }

                // 4. 再查 history 子集合，時間範圍查詢＋排序
                firestore.collection("locations")
                    .document(userId)
                    .collection("history")
                    .whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener { snap ->
                        val rawList = snap.documents.mapNotNull { doc ->
                            val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                            val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                            val ts  = doc.getTimestamp("timestamp") ?: return@mapNotNull null
                            SharedLocation(
                                user      = sharedUser,
                                uid       = userId,
                                latLng    = LatLng(lat, lng),
                                timestamp = ts
                            )
                        }
                        val clusters = clusterLocations(rawList, radiusMeters = 60f)
                        onResult(clusters)
                    }
            }
    }

    private fun clusterLocations(
        list: List<SharedLocation>,
        radiusMeters: Float = 60f
    ): List<LocationCluster> {
        if (list.isEmpty()) return emptyList()

        val clusters = mutableListOf<LocationCluster>()
        // 以第一筆當作第一個 cluster 的中心
        var curCenterLat = list[0].latLng.latitude
        var curCenterLng = list[0].latLng.longitude
        var startTs = list[0].timestamp ?: return emptyList()
        var endTs   = startTs

        val results = FloatArray(1)
        for (i in 1 until list.size) {
            val loc = list[i]
            val ts  = loc.timestamp ?: continue

            // 計算與目前中心的距離
            android.location.Location.distanceBetween(
                curCenterLat, curCenterLng,
                loc.latLng.latitude, loc.latLng.longitude,
                results
            )
            val dist = results[0]

            if (dist <= radiusMeters) {
                // within threshold：延長這個 cluster 的結束時間
                endTs = ts
            } else {
                // 超出範圍：先把上一個 cluster 存起來
                clusters.add(LocationCluster(LatLng(curCenterLat, curCenterLng), startTs, endTs,""))
                // 重置新 cluster 的中心與時間
                curCenterLat = loc.latLng.latitude
                curCenterLng = loc.latLng.longitude
                startTs = ts
                endTs   = ts
            }
        }
        // 最後一個 cluster
        clusters.add(LocationCluster(LatLng(curCenterLat, curCenterLng), startTs, endTs,""))
        return clusters
    }


}