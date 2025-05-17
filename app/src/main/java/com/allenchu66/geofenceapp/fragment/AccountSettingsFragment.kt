package com.allenchu66.geofenceapp.fragment

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import com.allenchu66.geofenceapp.GeofenceHelper
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.activity.MainActivity
import com.allenchu66.geofenceapp.database.GeofenceDatabase
import com.allenchu66.geofenceapp.databinding.DialogCropImageBinding
import com.allenchu66.geofenceapp.databinding.FragmentAccountSettingsBinding
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

class AccountSettingsFragment : Fragment() {
    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()

    private lateinit var geofenceHelper: GeofenceHelper

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { showCropDialog(it) }
        }

    private val options = CropImageOptions(
        // 固定寬高比
        fixAspectRatio        = true,
        aspectRatioX          = 1,
        aspectRatioY          = 1,
        cropShape             = CropImageView.CropShape.OVAL,
        guidelines            = CropImageView.Guidelines.ON,
        outputCompressFormat  = Bitmap.CompressFormat.JPEG,
        outputCompressQuality = 90
    )

    private fun showCropDialog(sourceUri: Uri) {
        val dialogBinding = DialogCropImageBinding
            .inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cropImageView.setImageUriAsync(sourceUri)
        dialogBinding.cropImageView.setImageCropOptions(options)

        dialogBinding.btnConfirm.setOnClickListener {
            dialogBinding.cropImageView.croppedImageAsync()
        }

        dialogBinding.cropImageView
            .setOnCropImageCompleteListener { _, result ->
                result.uriContent?.let { croppedUri ->
                    uploadImageToFirebase(croppedUri)
                    dialog.dismiss()
                } ?: run {
                    Toast.makeText(
                        requireContext(),
                        "裁切失敗：${result.error?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        dialog.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = Room.databaseBuilder(
            requireContext().applicationContext,
            GeofenceDatabase::class.java,
            "app_db"
        ).build()
        val localRepo = GeofenceLocalRepository(db.geofenceDao())

        geofenceHelper = GeofenceHelper(requireContext(), localRepo)

        val user = auth.currentUser
        binding.textEmail.text = user?.email ?: "No email"
        binding.textNickname.setText(user?.displayName ?: "Untitled")

        if (user?.photoUrl != null) {
            Glide.with(this)
                .load(user.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(binding.accountImage)
        } else {
            binding.accountImage.setImageResource(R.drawable.ic_default_avatar)
        }

        binding.btnSave.setOnClickListener {
            val newNickname = binding.textNickname.text.toString()
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newNickname)
                .build()

            user?.updateProfile(profileUpdates)
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = user.uid
                        val userDoc = Firebase.firestore.collection("users").document(uid)
                        userDoc.set(
                            mapOf(
                                "displayName"  to newNickname,
                            ),
                            SetOptions.merge()
                        )

                        (activity as? MainActivity)?.updateProfileUI(FirebaseAuth.getInstance().currentUser)
                        Toast.makeText(requireContext(), "儲存成功", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.mapFragment)
                    } else {
                        Toast.makeText(requireContext(), "儲存失敗", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            geofenceHelper.removeAllGeofences()
            findNavController().navigate(R.id.action_accountSettingsFragment_to_loginFragment)
        }

        binding.accountImage.setOnClickListener {
           pickImage.launch("image/*")
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun uploadImageToFirebase(uri: Uri) {
        val user = auth.currentUser ?: run {
            Toast.makeText(requireContext(), "請先登入", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid

        val storageRef = Firebase.storage
            .reference
            .child("photos/$uid.jpg")

        val progressView = layoutInflater.inflate(R.layout.dialog_upload_progress, null)
        val progressBar = progressView.findViewById<ProgressBar>(R.id.progressBarDialog)
        val tvPercent   = progressView.findViewById<TextView>(R.id.tvPercent)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .create()
        dialog.show()

        storageRef.putFile(uri)
            .addOnFailureListener { e ->
                dialog.dismiss()
                Toast.makeText(requireContext(), "上傳失敗：${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnProgressListener { snapshot ->
                val percent = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                progressBar.progress = percent
                tvPercent.text = "$percent%"
            }
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        progressBar.progress = 100
                        tvPercent.text = "100%"

                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setPhotoUri(downloadUri)
                            .build()

                        user.updateProfile(profileUpdates)
                            .addOnCompleteListener { updateTask ->
                                dialog.dismiss()
                                if (updateTask.isSuccessful) {
                                    val uid = user.uid
                                    val userDoc = Firebase.firestore.collection("users").document(uid)
                                    userDoc.set(
                                        mapOf(
                                            "photoUri" to (user.photoUrl?.toString().orEmpty())
                                        ),
                                        SetOptions.merge()
                                    )

                                    Toast.makeText(requireContext(), "大頭貼更新成功", Toast.LENGTH_SHORT).show()
                                    if (user.photoUrl != null) {
                                        Glide.with(this)
                                            .load(user.photoUrl)
                                            .circleCrop()
                                            .placeholder(R.drawable.ic_default_avatar)
                                            .error(R.drawable.ic_default_avatar)
                                            .into(binding.accountImage)
                                        (activity as? MainActivity)?.updateProfileUI(FirebaseAuth.getInstance().currentUser)
                                    } else {
                                        binding.accountImage.setImageResource(R.drawable.ic_default_avatar)
                                    }
                                } else {
                                    Toast.makeText(requireContext(), "更新大頭貼失敗", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "取得下載 URL 失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}