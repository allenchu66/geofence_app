package com.allenchu66.geofenceapp.repository

import android.util.Log
import com.allenchu66.geofenceapp.model.ShareRequest
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.model.SharedUser
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
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
                        .addSnapshotListener { locSnap, error ->
                            Log.d("Repo-LocSnap", "uid=$uid exists=${locSnap?.exists()} err=$error")
                            if (locSnap != null && locSnap.exists()) {
                                val lat = locSnap.getDouble("latitude") ?: return@addSnapshotListener
                                val lng = locSnap.getDouble("longitude") ?: return@addSnapshotListener
                                val ts  = locSnap.getTimestamp("timestamp")

                                locationMap[uid] = LatLng(lat, lng)
                                timestampMap[uid] = ts

                                Log.d("Repo-Location", "for $uid -> $lat,$lng $timestampMap")
                                emitSharedLocations(onUpdate, userMap, locationMap, timestampMap)
                            }
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
}