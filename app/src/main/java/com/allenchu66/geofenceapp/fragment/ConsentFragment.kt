package com.allenchu66.geofenceapp.fragment

import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.activity.MainActivity
import com.allenchu66.geofenceapp.databinding.FragmentConsentBinding

class ConsentFragment : Fragment() {

    private var _binding: FragmentConsentBinding? = null
    private val binding get() = _binding!!

    private val sharedPref by lazy {
        requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val htmlText = """
            <p>我們重視您的隱私權。本應用程式會持續追蹤您的位置，並上傳至雲端，用於：</p>
            <ul>
              <li>與您選擇的好友共享即時位置</li>
              <li>提供地理圍欄（Geofence）進入／離開通知</li>
            </ul>
            <p>本 App 會在背景執行，並持續定位與資料上傳。我們不會與第三方共享資料。</p>
            <p>詳情請參閱 <a href="https://allenchu66.github.io/geofence_app/">隱私權政策</a></p>
        """.trimIndent()

        binding.tvConsent.text = HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.tvConsent.movementMethod = LinkMovementMethod.getInstance()

        binding.btnAgree.setOnClickListener {
            if (!binding.checkboxAgree.isChecked) {
                Toast.makeText(requireContext(), "請勾選同意後繼續", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sharedPref.edit().putBoolean("hasConsented", true).apply()
            (activity as? MainActivity)?.startPermissionFlow()
            findNavController().navigate(R.id.action_consentFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
