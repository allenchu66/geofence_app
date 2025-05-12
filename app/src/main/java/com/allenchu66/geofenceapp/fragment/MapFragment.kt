package com.allenchu66.geofenceapp.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.transition.Transition
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.databinding.FragmentMapBinding
import com.allenchu66.geofenceapp.model.SharedUser
import com.allenchu66.geofenceapp.repository.LocationRepository
import com.allenchu66.geofenceapp.repository.SharedUserRepository
import com.allenchu66.geofenceapp.viewModel.MapViewModel
import com.allenchu66.geofenceapp.viewModel.MapViewModelFactory
import com.allenchu66.geofenceapp.viewModel.SharedUserViewModel
import com.allenchu66.geofenceapp.viewModel.SharedUserViewModelFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleMap: GoogleMap
    private lateinit var viewModel: MapViewModel
    private val markerMap = mutableMapOf<String, Marker>()

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val UPDATE_INTERVAL_MS = 60_000L
    private val handler = Handler()
    private lateinit var locationUpdateRunnable: Runnable

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private val sharedUserVM: SharedUserViewModel by activityViewModels {
        SharedUserViewModelFactory(SharedUserRepository())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 ViewModel
        val repository = LocationRepository()
        val factory = MapViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MapViewModel::class.java]

        sharedUserVM.loadSharedUsers()

        // 初始化地圖 Fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 觀察共享好友位置資料
        viewModel.sharedLocations.observe(viewLifecycleOwner) {  locations ->

            locations.forEach { shared ->
                updateOrAddMarker(
                    userId   = shared.uid,
                    latLng   = shared.latLng,
                    photoUrl = shared.user.photoUri
                )
            }

            val sharedUids = locations.map { it.uid }.toSet()
            val iterator = markerMap.entries.iterator()
            while (iterator.hasNext()) {
                val (uid, marker) = iterator.next()
                if (uid !in sharedUids) {
                    marker.remove()
                    iterator.remove()
                }
            }
        }

        // 定時上傳自己的位置
        locationUpdateRunnable = object : Runnable {
            @SuppressLint("MissingPermission")
            override fun run() {
                getCurrentLocationAndUpload()
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
        handler.post(locationUpdateRunnable)

        sheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.btnCloseSheet.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    fun expandSettingsSheet(sharedUser: SharedUser) {
        val meUid = FirebaseAuth.getInstance().currentUser?.uid
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        if(sharedUser.status == "accepted"){
            val marker = markerMap[sharedUser.uid]
            if (marker != null) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(marker.position, 17f)
                )
            }
        }

        binding.apply {
            // Avatar
            Glide.with(this@MapFragment)
                .load(sharedUser.photoUri)
                .circleCrop()
                .into(imgUserAvatar)

            // Name & Email
            textUserName.text  = sharedUser.displayName
            textUserEmail.text = sharedUser.email

            // 先隱藏／顯示切換用 Switch
            btnToggleShare.visibility = View.VISIBLE
            btnDecline.visibility      = View.GONE
            // 切換按鈕
            when (sharedUser.status) {
                // 1. 還在 pending
                "pending" -> {
                    if (sharedUser.inviter == meUid) {
                        // 我是邀請者 → 「取消邀請」
                        btnToggleShare.text = "取消邀請"
                        btnToggleShare.setOnClickListener {
                            sharedUserVM.updateShareRequestStatus(sharedUser.email, "declined")
                        }
                    } else {
                        // 對方邀請我 → 顯示「接受」＋「拒絕」
                        btnToggleShare.text = "接受邀請"
                        btnToggleShare.setOnClickListener {
                            sharedUserVM.updateShareRequestStatus(sharedUser.email, "accepted")
                        }
                        btnDecline.apply {
                            visibility = View.VISIBLE
                            text = "拒絕邀請"
                            setOnClickListener {
                                sharedUserVM.updateShareRequestStatus(sharedUser.email, "declined")
                            }
                        }
                    }
                }

                // 2. 已 accepted
                "accepted" -> {
                    // 正在共享 → 顯示一個開關或「停止共享」按鈕
                    btnToggleShare.text = "停止共享"
                    btnToggleShare.setOnClickListener {
                        sharedUserVM.updateShareRequestStatus(sharedUser.email, "declined")
                    }
                }

                // 3. 已 declined
                "declined" -> {
                    // 已拒絕 → 「重新邀請」
                    btnToggleShare.text = "重新邀請"
                    btnToggleShare.setOnClickListener {
                        sharedUserVM.sendShareRequest(sharedUser.email)
                    }
                }

                else -> {
                    // 其他 fallback
                    btnToggleShare.text = "邀請共享"
                    btnToggleShare.setOnClickListener {
                        sharedUserVM.sendShareRequest(sharedUser.email)
                    }
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            googleMap.isMyLocationEnabled = true
            moveToCurrentLocation()
        }

        // 地圖就緒後載入共享好友位置
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            viewModel.loadSharedLocations(uid)
        }
    }

    // 定位並移動視角到自己位置
    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                    FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
                        viewModel.updateLocationToFirestore(userId, latLng.latitude, latLng.longitude)
                    }
                } else {
                    Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 取得位置並上傳
    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndUpload() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                viewModel.updateLocationToFirestore(userId, location.latitude, location.longitude)
            }
        }
    }

    // 新增或更新 Marker
    private fun updateOrAddMarker(userId: String, latLng: LatLng, photoUrl: String) {
        Glide.with(this)
            .asBitmap()
            .load(photoUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>(60, 60) {
                override fun onResourceReady(
                    bitmap: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                ) {
                    val markerBmp = getMarkerBitmapFromView(bitmap)
                    val icon = BitmapDescriptorFactory.fromBitmap(markerBmp)

                    val existing = markerMap[userId]
                    if (existing != null) {
                        existing.position = latLng
                        existing.setIcon(icon)
                    } else {
                        val marker = googleMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("Shared User: $userId")
                                .icon(icon)
                        )
                        marker?.let { markerMap[userId] = it }
                    }
                    // 重新調整縮放（可選）
                    //zoomToFitAllMarkers()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    private fun getMarkerBitmapFromView(photo: Bitmap): Bitmap {
        // Inflate
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.marker_layout, null, false)
        val iv = view.findViewById<ImageView>(R.id.image_avatar)
        iv.setImageBitmap(photo)

        // 測量、佈局、畫到 Canvas
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth, view.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    // 自動調整視角包住所有 Marker
    private fun zoomToFitAllMarkers() {
        if (markerMap.isEmpty()) return
        val builder = LatLngBounds.builder()
        markerMap.values.forEach { marker -> builder.include(marker.position) }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    // 權限處理結果回調
    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (::googleMap.isInitialized) {
                    googleMap.isMyLocationEnabled = true
                    moveToCurrentLocation()
                }
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(locationUpdateRunnable)
        _binding = null
    }
}
