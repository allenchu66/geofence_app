package com.allenchu66.geofenceapp.repository

import android.util.Log
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

    fun listenToFriendLocations(
        friendUids: List<String>,
        onUpdate: (Map<String, LatLng>) -> Unit
    ) {
        val locationMap = mutableMapOf<String, LatLng>()

        friendUids.forEach { uid ->
            firestore.collection("locations").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val lat = snapshot.getDouble("latitude") ?: return@addSnapshotListener
                        val lng = snapshot.getDouble("longitude") ?: return@addSnapshotListener
                        locationMap[uid] = LatLng(lat, lng)
                        onUpdate(locationMap)
                    }
                }
        }
    }
}