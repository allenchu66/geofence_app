package com.allenchu66.geofenceapp.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.Transition
import android.util.Log
import android.util.StateSet
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
import androidx.room.Room
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.database.GeofenceDatabase
import com.allenchu66.geofenceapp.databinding.FragmentMapBinding
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.model.SharedUser
import com.allenchu66.geofenceapp.repository.GeofenceLocalRepository
import com.allenchu66.geofenceapp.repository.GeofenceRepository
import com.allenchu66.geofenceapp.repository.LocationRepository
import com.allenchu66.geofenceapp.repository.SharedUserRepository
import com.allenchu66.geofenceapp.viewModel.GeofenceViewModel
import com.allenchu66.geofenceapp.viewModel.GeofenceViewModelFactory
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val DEFAULT_RADIUS = 30f
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleMap: GoogleMap
    private lateinit var mapViewModel: MapViewModel

    private lateinit var geofenceViewModel: GeofenceViewModel

    private val markerMap = mutableMapOf<String, Marker>()

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val UPDATE_INTERVAL_MS = 60_000L
    private val handler = Handler()
    private lateinit var locationUpdateRunnable: Runnable

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private var savedGeofenceData: GeofenceData? = null

    private var geofenceMarker: Marker? = null
    private var geofenceCircle: Circle? = null

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
        mapViewModel = ViewModelProvider(this, factory)[MapViewModel::class.java]

        val db = Room.databaseBuilder(
            requireContext().applicationContext,
            GeofenceDatabase::class.java,
            "app_db"
        ).build()

        val geofenceLocalRepo = GeofenceLocalRepository(db.geofenceDao())

        val geofenceRepo = GeofenceRepository()
        val geofenceViewModelFactory =
            GeofenceViewModelFactory(requireActivity().application,geofenceLocalRepo ,geofenceRepo)
        geofenceViewModel =
            ViewModelProvider(this, geofenceViewModelFactory).get(GeofenceViewModel::class.java)

        sharedUserVM.loadSharedUsers()

        // 初始化地圖 Fragment
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 觀察共享好友位置資料
        mapViewModel.sharedLocations.observe(viewLifecycleOwner) { locations ->
            locations.forEach { shared ->
                updateOrAddMarker(
                    userId = shared.uid,
                    latLng = shared.latLng,
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
        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val bottomPadding = if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheet.height
                } else {
                    0
                }
                googleMap.setPadding(0, 0, 0, bottomPadding)
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // 也可以在滑動時跟著 slotOffset 動態更新
                adjustMapPadding(bottomSheet, slideOffset)
            }
        })
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.btnCloseSheet.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        geofenceViewModel.ownerGeofences.observe(viewLifecycleOwner) { fences ->
            val fence = fences.firstOrNull()  // 挑最新那一筆
            savedGeofenceData = fence
            fence?.let {
                binding.etGeofenceLocationName.setText(it.locationName)
                binding.tvLatLng.text = "Lat: %.5f, Lng: %.5f".format(it.latitude, it.longitude)

                binding.sliderRadius.value = it.radius
                binding.tvGeofenceRadius.text = "${it.radius} m"

                binding.chipEnter.isChecked = it.transition.contains("enter")
                binding.chipExit.isChecked  = it.transition.contains("exit")
            }
        }

        checkFirebaseMessageToken()

        // 地圖就緒後載入共享好友位置
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            mapViewModel.loadSharedLocations(uid)
            geofenceViewModel.loadIncomingGeofences()
        }
    }

    private fun adjustMapPadding(
        sheet: View,
        slideOffset: Float = 1f // collapse 時 slideOffset=0, expand 時=1
    ) {
        val bottomPadding = (sheet.height * slideOffset).toInt()
        googleMap.setPadding(0, 0, 0, bottomPadding)
    }

    private fun checkFirebaseMessageToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM", "Fetching FCM token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    Log.w("FCM", "Cannot upload token: user not signed in")
                    return@addOnCompleteListener
                }

                val db = Firebase.firestore
                db.collection("users")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d("FCM", "Token successfully saved to Firestore")
                    }
                    .addOnFailureListener { e ->
                        // 如果文件還不存在，也可以改用 set + merge
                        db.collection("users")
                            .document(uid)
                            .set(mapOf("fcmToken" to token), SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("FCM", "Token saved via set/merge")
                            }
                            .addOnFailureListener { ex ->
                                Log.e("FCM", "Failed to save token", ex)
                            }
                        Log.e("FCM", "Failed to update token field, will try set()", e)
                    }
            }

    }

    fun expandSettingsSheet(sharedUser: SharedUser) {
        val meUid = FirebaseAuth.getInstance().currentUser?.uid
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        if (sharedUser.status == "accepted") {
            val marker = markerMap[sharedUser.uid]
            if (marker != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(marker.position, 17f)
                    )
                },200)
            }
        }

        geofenceViewModel.loadOwnerGeofencesForTarget(sharedUser.uid)

        binding.apply {
            // Avatar
            Glide.with(this@MapFragment)
                .load(sharedUser.photoUri)
                .circleCrop()
                .into(imgUserAvatar)

            // Name & Email
            textUserName.text = sharedUser.displayName
            textUserEmail.text = sharedUser.email

            // 先隱藏／顯示切換用 Switch
            btnToggleShare.visibility = View.VISIBLE
            btnDecline.visibility = View.GONE
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

            binding.btnSave.setOnClickListener {
                val nameInput =
                    etGeofenceLocationName.text.toString().takeIf { it.isNotBlank() } ?: "default"
                val center = geofenceMarker?.position
                val radius = geofenceCircle?.radius?.toFloat() ?: DEFAULT_RADIUS
                val transitions = mutableListOf<String>().apply {
                    if (chipEnter.isChecked) add("enter")
                    if (chipExit.isChecked) add("exit")
                }
                if (center == null) {
                    Toast.makeText(requireContext(), "請先在地圖上選擇一個範圍", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                if (transitions.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "請至少選擇 進入 或 離開 觸發事件",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                geofenceViewModel.uploadGeofence(
                    targetUid = sharedUser.uid,
                    lat = center.latitude,
                    lng = center.longitude,
                    radius = radius,
                    name = nameInput,                      // 或從 UI 取得
                    transition = transitions     // 或從 UI 取得
                )
            }

            binding.btnEditGeofence.setOnClickListener {
                val marker = markerMap[sharedUser.uid]
                val center = savedGeofenceData
                    ?.let { LatLng(it.latitude, it.longitude) }
                    ?: marker?.position
                val radius = savedGeofenceData?.radius ?: DEFAULT_RADIUS
                if (center != null) {
                    addOrResetGeofence(center, radius)
                    binding.tvLatLng.text =
                        "Lat: %.5f, Lng: %.5f".format(center.latitude, center.longitude)
                }
            }

            binding.sliderRadius.addOnChangeListener { _, value, _ ->
                updateCircleRadius(value.toDouble())
            }
        }
    }

    private val geofenceDragListener = object : GoogleMap.OnMarkerDragListener {
        override fun onMarkerDragStart(marker: Marker) = Unit
        override fun onMarkerDrag(marker: Marker) {
            if (marker == geofenceMarker) {
                geofenceCircle?.center = marker.position
                binding.tvLatLng.text =
                    "Lat: %.5f, Lng: %.5f".format(
                        marker.position.latitude,
                        marker.position.longitude
                    )
            }
        }

        override fun onMarkerDragEnd(marker: Marker) {
            if (marker == geofenceMarker) {
                geofenceCircle?.center = marker.position
            }
        }
    }


    private fun addOrResetGeofence(center: LatLng, radius: Float) {
        // 移除舊的
        geofenceMarker?.remove()
        geofenceCircle?.remove()

        // 新增 marker（可拖放）
        geofenceMarker = googleMap.addMarker(
            MarkerOptions()
                .position(center)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        )

        // 新增 circle
        geofenceCircle = googleMap.addCircle(
            CircleOptions()
                .center(center)
                .radius(radius.toDouble())
                .strokeWidth(2f)
                .strokeColor(Color.parseColor("#EA0000"))
                .fillColor(Color.parseColor("#4DFF7575"))
        )
    }

    private fun updateCircleRadius(radius: Double) {
        geofenceCircle?.radius = radius
        binding.tvGeofenceRadius.text = "${radius} m"
    }


    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.isBuildingsEnabled = false


        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            googleMap.isMyLocationEnabled = true
            moveToCurrentLocation()
        }

        googleMap.setOnMarkerDragListener(geofenceDragListener)
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
                        mapViewModel.updateLocationToFirestore(
                            userId,
                            latLng.latitude,
                            latLng.longitude
                        )
                    }
                } else {
                    Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Location error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    // 取得位置並上傳
    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndUpload() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userId =
                    FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                mapViewModel.updateLocationToFirestore(
                    userId,
                    location.latitude,
                    location.longitude
                )
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
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(locationUpdateRunnable)
        _binding = null
    }
}
