package com.allenchu66.geofenceapp.repository

import android.util.Log
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.model.SharedUser
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LocationRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun uploadLocation(userId: String, lat: Double, lng: Double) {
        val locationData = hashMapOf(
            "user_id" to userId,
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("locations").document(userId)
            .set(locationData)
            .addOnSuccessListener { Log.d("LocationUpload", "Success") }
            .addOnFailureListener { e -> Log.e("LocationUpload", "Error", e) }
    }

    fun getSharedFriendUids(currentUserId: String, callback: (List<String>) -> Unit) {
        firestore.collection("users").document(currentUserId)
            .collection("shared_friends")
            .get()
            .addOnSuccessListener { snapshot ->
                val friendUids = snapshot.documents.mapNotNull { it.id }
                callback(friendUids)
            }
    }

    /**
     * 同時監聽 shared_friends 裡的用户资料 & locations 裡的位置，
     * 然後把 SharedUser + LatLng 組成 SharedLocation 回傳
     */
    fun listenToSharedLocations(
        currentUserId: String,
        onUpdate: (List<SharedLocation>) -> Unit
    ) {
        Log.d("20250511",currentUserId)

        val userMap = mutableMapOf<String, SharedUser>()
        val locationMap = mutableMapOf<String, LatLng>()

        firestore.collection("users")
            .document(currentUserId)
            .collection("shared_friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                userMap.clear()
                locationMap.clear()

                val friendUids = snapshot.documents.map { it.id }

                friendUids.forEach { uid ->
                    firestore.collection("users")
                        .document(uid)
                        .addSnapshotListener { userSnap, _ ->
                            if (userSnap != null && userSnap.exists()) {
                                userSnap.toObject(SharedUser::class.java)?.let { user ->
                                    userMap[uid] = user
                                    // 如果已經有他的地點了，就 emit
                                    locationMap[uid]?.let { _ -> emit(onUpdate, userMap, locationMap) }
                                }
                            }
                        }
                    firestore.collection("locations")
                        .document(uid)
                        .addSnapshotListener { locSnap, _ ->
                            if (locSnap != null && locSnap.exists()) {
                                val lat = locSnap.getDouble("latitude") ?: return@addSnapshotListener
                                val lng = locSnap.getDouble("longitude") ?: return@addSnapshotListener
                                locationMap[uid] = LatLng(lat, lng)
                                // 如果已經有他的 user 資訊了，就 emit
                                userMap[uid]?.let { _ -> emit(onUpdate, userMap, locationMap) }
                            }
                        }
                }
            }
    }

    private fun emit(
        onUpdate: (List<SharedLocation>) -> Unit,
        userMap: Map<String, SharedUser>,
        locationMap: Map<String, LatLng>
    ) {
        val combined = userMap.mapNotNull { (id, user) ->
            locationMap[id]?.let { latLng ->
                SharedLocation(user, id, latLng)
            }
        }
        onUpdate(combined)
    }

}