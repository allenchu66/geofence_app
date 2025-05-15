package com.allenchu66.geofenceapp.activity

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
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
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.adapter.SharedUserAdapter
import com.allenchu66.geofenceapp.databinding.ActivityMainBinding
import com.allenchu66.geofenceapp.fragment.MapFragment
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
                loadUserPhotoInto(imageUserPhoto, photoUri)
                loadUserPhotoInto(binding.btnOpenDrawer, photoUri)
            } else {
                imageUserPhoto.setImageResource(R.drawable.ic_default_avatar)
                binding.btnOpenDrawer.setImageResource(R.drawable.ic_default_avatar)
            }
        }
    }

    private fun loadUserPhotoInto(target: ImageView, photoUri: Uri?) {
        Glide.with(this)
            .load(photoUri)
            .circleCrop()
            .placeholder(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar)
            .into(target)
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
        setupDrawer()
        navigateIfLoggedIn()
        requestLocationPermissions()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val decorView = window.decorView
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

    }

    private fun initViewModel(){
        val repository = SharedUserRepository()
        val factory = SharedUserViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory).get(SharedUserViewModel::class.java)

        viewModel.loadSharedUsers()
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            null,
            R.string.drawer_open,
            R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.btnOpenDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val navView = binding.navView
        val customDrawerView = layoutInflater.inflate(R.layout.drawer_content, navView, false)
        navView.removeAllViews()
        navView.addView(customDrawerView)

        val adapter = SharedUserAdapter(emptyList()) { user ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            val navHost = supportFragmentManager
                .findFragmentById(R.id.fragmentContainerView) as NavHostFragment

            val mapFrag = navHost
                .childFragmentManager
                .fragments
                .firstOrNull { it is MapFragment } as? MapFragment

            mapFrag?.expandSettingsSheet(user)
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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.mapFragment -> {
                    // 進入 MapFragment：解鎖 Drawer、顯示按鈕
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    binding.btnOpenDrawer.visibility = View.VISIBLE
                }
                else -> {
                    // 其他 Fragment（例如 Login）：鎖住 Drawer、隱藏按鈕
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    binding.btnOpenDrawer.visibility = View.GONE
                }
            }
        }

        val btnAccountSetting = customDrawerView.findViewById<ImageButton>(R.id.btn_account_settings)
        btnAccountSetting.setOnClickListener{
            binding.drawerLayout.closeDrawers()
            navController.navigate(R.id.accountSettingsFragment)
        }

        val currentUser = FirebaseAuth.getInstance().currentUser

        tvUserEmail = customDrawerView.findViewById<TextView>(R.id.drawer_user_email)
        tvUserNickname = customDrawerView.findViewById<TextView>(R.id.drawer_user_nickname)
        imageUserPhoto = customDrawerView.findViewById<ImageView>(R.id.drawer_user_photo)

        if (currentUser != null) {
           updateProfileUI(currentUser)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // 這裡把 paddingTop 設成 statusBarHeight，讓整塊 navView 往下推
            v.setPadding(v.paddingLeft, 0, v.paddingRight, v.paddingBottom)
            // 回傳 insets 代表「我還沒消耗其他 inset」
            insets
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "請允許顯示通知", Toast.LENGTH_SHORT).show()
        }
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
