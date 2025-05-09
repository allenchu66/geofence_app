package com.allenchu66.geofenceapp.fragment

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
                Toast.makeText(requireContext(), "登入成功", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_loginFragment_to_mapFragment)
            } else {
                Toast.makeText(requireContext(), "登入失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}