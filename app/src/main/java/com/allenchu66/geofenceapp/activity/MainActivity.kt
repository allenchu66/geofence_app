package com.allenchu66.geofenceapp.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.databinding.ActivityMainBinding
import com.allenchu66.geofenceapp.service.LocationUpdateService
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

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

        setupToolbarAndDrawer()
        navigateIfLoggedIn()
        requestLocationPermissions()
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
}
