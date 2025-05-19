package com.allenchu66.geofenceapp.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.databinding.FragmentLoginBinding
import com.allenchu66.geofenceapp.repository.FirebaseAuthRepository
import com.allenchu66.geofenceapp.viewModel.LoginViewModel
import com.allenchu66.geofenceapp.viewModel.LoginViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LoginViewModel


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val factory = LoginViewModelFactory(FirebaseAuthRepository())
        viewModel = ViewModelProvider(this, factory)[LoginViewModel::class.java]

        binding.loginButton.setOnClickListener {
            val email = binding.emailEdit.text.toString().trim()
            val password = binding.passwordEdit.text.toString().trim()

            viewModel.login(email, password)
        }
        viewModel.loginSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    createUserIfNotExists(user)
                }

                Toast.makeText(requireContext(), "登入成功", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_loginFragment_to_mapFragment)
            } else {
                Toast.makeText(requireContext(), "登入失敗", Toast.LENGTH_SHORT).show()
            }
        }

        binding.registerButton.setOnClickListener{
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://allenchu66.github.io/geofence_app/"))
            startActivity(intent)
        }

    }

    private fun createUserIfNotExists(user: FirebaseUser) {
        val db = FirebaseFirestore.getInstance()
        val userDoc = db.collection("users").document(user.uid)

        userDoc.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                val newUser = hashMapOf(
                    "email" to user.email,
                    "name" to (user.displayName ?: ""),
                    "created_at" to FieldValue.serverTimestamp()
                )
                userDoc.set(newUser)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}