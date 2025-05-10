package com.allenchu66.geofenceapp.repository

import android.util.Log
import com.allenchu66.geofenceapp.model.SharedUser
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class SharedUserRepository {
    private val firestore = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    fun getSharedUsers(callback: (List<SharedUser>) -> Unit) {
        val meUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sharedRef = firestore
            .collection("users").document(meUid)
            .collection("shared_friends")

        sharedRef.addSnapshotListener { snap, error ->
            if (error != null) return@addSnapshotListener
            if (snap == null) {
                callback(emptyList())
                return@addSnapshotListener
            }

            val tempList = mutableListOf<SharedUser>()
            val total = snap.size()
            if (total == 0) {
                callback(emptyList()); return@addSnapshotListener
            }

            for (doc in snap.documents) {
                val friendUid = doc.id
                val status    = doc.getString("status").orEmpty()

                firestore.collection("users")
                    .document(friendUid)
                    .get()
                    .addOnSuccessListener { userDoc ->
                        val email       = userDoc.getString("email").orEmpty()
                        val displayName = userDoc.getString("displayName").orEmpty()
                        val photoUri    = userDoc.getString("photoUri")

                        tempList.add(
                            SharedUser(
                                email       = email,
                                displayName = displayName,
                                photoUri    = photoUri.orEmpty(),
                                status      = status
                            )
                        )
                        if (tempList.size == total) {
                            callback(tempList)
                        }
                    }
                    .addOnFailureListener {
                        tempList.add(SharedUser(status = status))
                        if (tempList.size == total) {
                            callback(tempList)
                        }
                    }
            }
        }
    }


    fun updateShareStatusByEmail(email: String, status: String, onFinish: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return
        val currentUid = currentUser.uid

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onFinish()
                    return@addOnSuccessListener
                }

                val targetDoc = snapshot.documents.first()
                val targetUid = targetDoc.id

                val batch = db.batch()

                // 自己的 shared_friends 對對方更新
                val selfRef = db.collection("users").document(currentUid)
                    .collection("shared_friends").document(targetUid)
                batch.update(selfRef, "status", status)

                // 對方的 shared_friends 對自己更新
                val targetRef = db.collection("users").document(targetUid)
                    .collection("shared_friends").document(currentUid)
                batch.update(targetRef, "status", status)

                batch.commit()
                    .addOnSuccessListener { onFinish() }
                    .addOnFailureListener { onFinish() }
            }
            .addOnFailureListener { onFinish() }
    }


    fun sendShareRequestByEmail(email: String, onResult: (Boolean, String) -> Unit) {
        firestore.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    onResult(false, "No user found with this email.")
                    return@addOnSuccessListener
                }

                val targetDoc = querySnapshot.documents.first()
                val targetUid = targetDoc.id
                val currentUid = auth.currentUser?.uid ?: return@addOnSuccessListener

                if (targetUid == currentUid) {
                    onResult(false, "You can't share with yourself.")
                    return@addOnSuccessListener
                }

                val toTarget  = hashMapOf(
                    "email" to auth.currentUser?.email,
                    "status" to "waiting",
                    "created_at" to FieldValue.serverTimestamp()
                )

                val toSelf = hashMapOf(
                    "email" to email,
                    "status" to "waiting",
                    "created_at" to FieldValue.serverTimestamp()
                )

                val batch = firestore.batch()

                val targetRef = firestore.collection("users")
                    .document(targetUid)
                    .collection("shared_friends")
                    .document(currentUid)
                val selfRef = firestore.collection("users")
                    .document(currentUid)
                    .collection("shared_friends")
                    .document(targetUid)

                batch.set(targetRef, toTarget)
                batch.set(selfRef, toSelf)

                batch.commit()
                    .addOnSuccessListener { onResult(true, "Invite sent.") }
                    .addOnFailureListener { e -> onResult(false, e.message ?: "Error") }
            }
            .addOnFailureListener { onResult(false, it.message ?: "Error")}
    }
}
