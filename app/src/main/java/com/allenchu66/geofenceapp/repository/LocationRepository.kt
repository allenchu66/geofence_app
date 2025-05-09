package com.allenchu66.geofenceapp.repository

import android.util.Log
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
}