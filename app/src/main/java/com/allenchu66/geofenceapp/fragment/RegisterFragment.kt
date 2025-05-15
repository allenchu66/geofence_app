package com.allenchu66.geofenceapp.fragment

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.activity.MainActivity
import com.allenchu66.geofenceapp.databinding.DialogCropImageBinding
import com.allenchu66.geofenceapp.databinding.FragmentRegisterBinding
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private var avatarUri: Uri? = null

    private val auth by lazy { FirebaseAuth.getInstance() }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { showCropDialog(it) }
        }

    private val options = CropImageOptions(
        fixAspectRatio = true,
        aspectRatioX = 1,
        aspectRatioY = 1,
        cropShape = CropImageView.CropShape.OVAL,
        guidelines = CropImageView.Guidelines.ON,
        outputCompressFormat = Bitmap.CompressFormat.JPEG,
        outputCompressQuality = 90
    )

    private fun showCropDialog(sourceUri: Uri) {
        val dialogBinding = DialogCropImageBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cropImageView.setImageUriAsync(sourceUri)
        dialogBinding.cropImageView.setImageCropOptions(options)
        dialogBinding.btnConfirm.setOnClickListener {
            dialogBinding.cropImageView.croppedImageAsync()
        }
        dialogBinding.cropImageView.setOnCropImageCompleteListener { _, result ->
            result.uriContent?.let { croppedUri ->
                avatarUri = croppedUri
                binding.imgAvatar.setImageURI(croppedUri)
                dialog.dismiss()
            } ?: run {
                Toast.makeText(requireContext(), "裁切失敗：${result.error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun uploadImageToFirebase(user: FirebaseUser, uri: Uri, nickname: String) {
        val uid = user.uid
        val storageRef = Firebase.storage.reference.child("photos/$uid.jpg")

        // 上傳進度對話框
        val progressView = layoutInflater.inflate(R.layout.dialog_upload_progress, null)
        val progressBar = progressView.findViewById<ProgressBar>(R.id.progressBarDialog)
        val tvPercent = progressView.findViewById<TextView>(R.id.tvPercent)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(progressView)
            .setCancelable(false)
            .create()
        dialog.show()

        storageRef.putFile(uri)
            .addOnProgressListener { snap ->
                val percent = (100.0 * snap.bytesTransferred / snap.totalByteCount).toInt()
                progressBar.progress = percent
                tvPercent.text = "$percent%"
            }
            .addOnFailureListener { e ->
                dialog.dismiss()
                Toast.makeText(requireContext(), "上傳失敗：${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        progressBar.progress = 100
                        tvPercent.text = "100%"

                        // 合并一次性更新 photoUri & displayName
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setPhotoUri(downloadUri)
                            .setDisplayName(nickname)
                            .build()

                        user.updateProfile(profileUpdates)
                            .addOnCompleteListener { updateTask ->
                                dialog.dismiss()
                                if (updateTask.isSuccessful) {
                                    // Firestore 同步使用者資料
                                    Firebase.firestore
                                        .collection("users")
                                        .document(uid)
                                        .set(
                                            mapOf(
                                                "photoUri" to downloadUri.toString(),
                                                "displayName" to nickname
                                            ),
                                            SetOptions.merge()
                                        )
                                    (activity as? MainActivity)
                                        ?.updateProfileUI(auth.currentUser)
                                    Toast.makeText(requireContext(), "註冊成功，正在跳轉...", Toast.LENGTH_SHORT).show()
                                    findNavController().navigate(R.id.action_registerFragment_to_mapFragment)
                                } else {
                                    Toast.makeText(requireContext(), "更新 Profile 失敗", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "取得下載 URL 失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun saveUserProfile(user: FirebaseUser, nickname: String) {
        // 沒有選擇照片，只更新 displayName
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(nickname)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Firebase.firestore
                        .collection("users")
                        .document(user.uid)
                        .set(
                            mapOf("displayName" to nickname),
                            SetOptions.merge()
                        )
                    (activity as? MainActivity)?.updateProfileUI(auth.currentUser)
                    Toast.makeText(requireContext(), "註冊成功，正在跳轉...", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_registerFragment_to_mapFragment)
                } else {
                    Toast.makeText(requireContext(), "更新 Profile 失敗", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentRegisterBinding.bind(view)

        binding.imgAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val nickname = binding.etNickname.text.toString().trim()
            val pwd = binding.etPassword.text.toString()
            val confirm = binding.etConfirm.text.toString()

            // 基本輸入驗證
            when {
                email.isEmpty() -> Toast.makeText(requireContext(), "請輸入 email", Toast.LENGTH_SHORT).show()
                nickname.isEmpty() -> Toast.makeText(requireContext(), "請輸入暱稱", Toast.LENGTH_SHORT).show()
                pwd.length < 6 -> Toast.makeText(requireContext(), "密碼至少六位", Toast.LENGTH_SHORT).show()
                pwd != confirm -> Toast.makeText(requireContext(), "密碼與確認不同", Toast.LENGTH_SHORT).show()
                else -> {
                    auth.createUserWithEmailAndPassword(email, pwd)
                        .addOnSuccessListener { result ->
                            result.user?.let { user ->
                                if (avatarUri != null) {
                                    uploadImageToFirebase(user, avatarUri!!, nickname)
                                } else {
                                    saveUserProfile(user, nickname)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "註冊失敗：${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
