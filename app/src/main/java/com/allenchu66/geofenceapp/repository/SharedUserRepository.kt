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
    private val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getSharedUsers(callback: (List<SharedUser>) -> Unit) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        firestore.collection("users")
            .document(currentUid)
            .collection("shared_friends")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(SharedUser::class.java) }
                    callback(list)
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
        db.collection("users")
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

                val batch = db.batch()

                val targetRef = db.collection("users")
                    .document(targetUid)
                    .collection("shared_friends")
                    .document(currentUid)
                val selfRef = db.collection("users")
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
