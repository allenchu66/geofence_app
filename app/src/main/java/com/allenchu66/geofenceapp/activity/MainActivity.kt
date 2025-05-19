package com.allenchu66.geofenceapp.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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

    /** 前景定位 + 通知權限 */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 前景定位
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            // 拿到前景定位後再去請求背景定位
            requestBackgroundLocation()
        } else {
            Toast.makeText(this, "請允許前景定位權限", Toast.LENGTH_SHORT).show()
        }

        // Android 13以上需要額外請求通知權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifyGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
            if (!notifyGranted) {
                Toast.makeText(this, "請允許顯示通知", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 背景定位權限 */
    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startLocationService()
        } else {
            // 如果User選了"不再詢問"，引導去設定
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                AlertDialog.Builder(this)
                    .setTitle("需要背景定位")
                    .setMessage("請到設定允許[背景定位]權限才能接收Geofence通知")
                    .setPositiveButton("前往設定") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                Toast.makeText(this, "未取得背景定位權限，背景Geofence通知功能將無法使用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        updateProfileUI(user)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        initViewModel()
        setupDrawer()

        // 檢查是否同意條款
        if (!checkConsentAndNavigate()) return

        navigateIfLoggedIn()

        //請求前景定位權限
        requestLocationPermissions()

        // Hide the status bar.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        actionBar?.hide()
    }

    private fun checkConsentAndNavigate(): Boolean {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasConsented = sharedPref.getBoolean("hasConsented", false)

        if (!hasConsented) {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.fragmentContainerView) as NavHostFragment
            val navController = navHostFragment.navController

            // 避免重複導向
            if (navController.currentDestination?.id != R.id.consentFragment) {
                navController.navigate(R.id.consentFragment)
            }
            return false
        }
        return true
    }


    fun updateProfileUI(user: FirebaseUser?) {
        user?.let {
            tvUserNickname.setText(it.displayName ?: "Untitled")
            tvUserEmail.text = user.email
            tvUserNickname.text = user.displayName.takeIf { !it.isNullOrBlank() } ?: "Untitled"

            val photoUri = user.photoUrl

            if (photoUri != null) {
                loadUserPhotoInto(imageUserPhoto, photoUri)
                //loadUserPhotoInto(binding.btnOpenDrawer, photoUri)
            } else {
                imageUserPhoto.setImageResource(R.drawable.ic_default_avatar)
                //binding.btnOpenDrawer.setImageResource(R.drawable.ic_default_avatar)
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

//        viewModel.shareResult.observe(this){ (success, message) ->
//            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
//        }

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
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }

        permissionLauncher.launch(perms.toTypedArray())
    }

    /**背景定位權限*/
    private fun requestBackgroundLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationService()
            return
        }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                // Android 9 以下
                startLocationService()
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                // Android 10
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            else -> {
                // Android 11 以上：引導至設定頁面
                AlertDialog.Builder(this)
                    .setTitle("需要背景定位")
                    .setMessage("Android 11 以上版本需至設定頁面手動開啟[背景定位] \n位置權限 -> 一率允許")
                    .setPositiveButton("前往設定") { _, _ ->
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationUpdateService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun showAddSharedUserDialog() {
        val input = EditText(this).apply {
            hint = "請輸入好友的email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(this)
            .setTitle("邀請好友")
            .setView(input)
            .setPositiveButton("傳送邀請") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    viewModel.sendShareRequest(email)
                } else {
                    Toast.makeText(this, "請輸入Email", Toast.LENGTH_SHORT).show()
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
