package com.allenchu66.geofenceapp.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.adapter.SharedUserAdapter
import com.allenchu66.geofenceapp.databinding.ActivityMainBinding
import com.allenchu66.geofenceapp.repository.SharedUserRepository
import com.allenchu66.geofenceapp.service.LocationUpdateService
import com.allenchu66.geofenceapp.viewModel.SharedUserViewModel
import com.allenchu66.geofenceapp.viewModel.SharedUserViewModelFactory
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var viewModel: SharedUserViewModel
    private lateinit var auth: FirebaseAuth

    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserNickname: TextView
    private lateinit var imageUserPhoto: ImageView

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        updateProfileUI(user)
    }

    fun updateProfileUI(user: FirebaseUser?) {
        user?.let {
            tvUserNickname.setText(it.displayName ?: "Untitled")
            tvUserEmail.text = user.email
            tvUserNickname.text = user.displayName.takeIf { !it.isNullOrBlank() } ?: "Untitled"

            val photoUri = user.photoUrl

            if (photoUri != null) {
                Glide.with(this)
                    .load(photoUri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(imageUserPhoto)
            } else {
                imageUserPhoto.setImageResource(R.drawable.ic_default_avatar)
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handleLocationPermissionsResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        initViewModel()
        setupToolbarAndDrawer()
        navigateIfLoggedIn()
        requestLocationPermissions()
    }

    private fun initViewModel(){
        val repository = SharedUserRepository()
        val factory = SharedUserViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(SharedUserViewModel::class.java)

        viewModel.loadSharedUsers()
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        val navView = binding.navView
        val customDrawerView = layoutInflater.inflate(R.layout.drawer_content, navView, false)
        navView.removeAllViews()
        navView.addView(customDrawerView)

        val adapter = SharedUserAdapter(emptyList()) { user ->
            when (user.status) {
                "pending" -> {
                    // 接受邀請（自己被邀請的人）
                    viewModel.updateShareStatus(user.email, "shared")
                }
                "waiting" -> {
                    // 自己取消邀請對方
                    viewModel.updateShareStatus(user.email, "cancelled")
                }
                "shared" -> {
                    // 雙方停止共享
                    viewModel.updateShareStatus(user.email, "none")
                }
                "declined", "cancelled", "none" -> {
                    // 重新邀請
                    viewModel.sendShareRequest(user.email)
                }
            }
        }

        val recyclerView = customDrawerView.findViewById<RecyclerView>(R.id.share_user_recyclerView)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.sharedUsers.observe(this) { userList ->
            adapter.updateList(userList)
        }

        viewModel.shareResult.observe(this){ (success, message) ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        val btn_add_shared_user = customDrawerView.findViewById<Button>(R.id.btn_add_shared_user)
        btn_add_shared_user.setOnClickListener{
            showAddSharedUserDialog()
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        val navController = navHostFragment.navController

        val btnAccountSetting = customDrawerView.findViewById<ImageButton>(R.id.btn_account_settings)
        btnAccountSetting.setOnClickListener{
            binding.drawerLayout.closeDrawers()
            navController.navigate(R.id.accountSettingsFragment)
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
           tvUserEmail = customDrawerView.findViewById<TextView>(R.id.drawer_user_email)
           tvUserNickname = customDrawerView.findViewById<TextView>(R.id.drawer_user_nickname)
           imageUserPhoto = customDrawerView.findViewById<ImageView>(R.id.drawer_user_photo)
           updateProfileUI(currentUser)
        }
    }

    private fun navigateIfLoggedIn() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.fragmentContainerView) as NavHostFragment
            val navController = navHostFragment.navController
            navController.navigate(R.id.action_loginFragment_to_mapFragment)
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        )
    }

    private fun handleLocationPermissionsResult(permissions: Map<String, Boolean>) {
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val background = permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false

        // Android 14+ 要加上 FOREGROUND_SERVICE_LOCATION 權限檢查
        val foregroundServiceGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if ((fine || coarse) && background && foregroundServiceGranted) {
            val serviceIntent = Intent(this, LocationUpdateService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            Toast.makeText(this, "請允許背景位置與前景服務權限", Toast.LENGTH_LONG).show()
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun showAddSharedUserDialog() {
        val input = EditText(this).apply {
            hint = "Enter friend's email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(this)
            .setTitle("Add Shared User")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    viewModel.sendShareRequest(email)
                } else {
                    Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }
    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

}
