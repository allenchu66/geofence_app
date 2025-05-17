package com.allenchu66.geofenceapp.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.transition.Transition
import android.util.Log
import android.util.StateSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.allenchu66.geofenceapp.R

import com.allenchu66.geofenceapp.databinding.FragmentMapBinding
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.model.SharedUser
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
import com.google.android.material.chip.Chip
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
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

    private var currentFenceId: String? = null
    private var isConfigVisible = false

    private var currentSharedUserUid: String? = null

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

        initMapViewModel()
        initGeofenceViewMode()

        sharedUserVM.loadSharedUsers()
        sharedUserVM.sharedUsers.observe(viewLifecycleOwner) { users ->
            // 只有 sheet 展開時才重整 UI
            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                currentSharedUserUid
                    ?.let { uid -> users.find { it.uid == uid } }
                    ?.let { updatedUser ->
                        // 重新渲染 bottom sheet
                        expandSettingsSheet(updatedUser)
                    }
            }
        }

        // 初始化地圖 Fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        //定時上傳現在位置到firestore
        setUploadCurrentLocationTimer()

        setUpBottomSheet()

        checkFirebaseMessageToken()

        // 地圖就緒後載入共享好友位置
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            mapViewModel.loadSharedLocations(uid)
            geofenceViewModel.loadIncomingGeofences()
        }
    }

    private fun setUpBottomSheet(){
        sheetBehavior = BottomSheetBehavior.from(binding.includeBottomSheet.bottomSheet)
        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val bottomPadding = if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheet.height
                } else {
                    0
                }
                googleMap.setPadding(0, 0, 0, bottomPadding)
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    // 清除地理圍欄標記與圓圈
                    geofenceMarker?.remove()
                    geofenceMarker = null
                    geofenceCircle?.remove()
                    geofenceCircle = null
                    // 如果你有存 savedGeofenceData，也可以一併清除
                    savedGeofenceData = null
                    currentFenceId = null
                    currentSharedUserUid = null
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // 也可以在滑動時跟著 slotOffset 動態更新
                adjustMapPadding(bottomSheet, slideOffset)
            }
        })
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        binding.includeBottomSheet.btnCloseSheet.setOnClickListener {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setUploadCurrentLocationTimer(){
        // 定時上傳自己的位置
        locationUpdateRunnable = object : Runnable {
            @SuppressLint("MissingPermission")
            override fun run() {
                getCurrentLocationAndUpload()
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
        handler.post(locationUpdateRunnable)
    }

    private fun initMapViewModel(){
        // 初始化 ViewModel
        val repository = LocationRepository()
        val factory = MapViewModelFactory(repository)
        mapViewModel = ViewModelProvider(this, factory)[MapViewModel::class.java]

        // 觀察共享好友位置資料
        mapViewModel.sharedLocations.observe(viewLifecycleOwner) { locations ->
            locations.forEach { shared ->
                updateOrAddMarker(
                    displayName = shared.user.displayName,
                    userId = shared.uid,
                    latLng = shared.latLng,
                    photoUrl = shared.user.photoUri,
                    timestamp = shared.timestamp
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

            currentSharedUserUid?.let { uid ->
                locations.firstOrNull { it.uid == uid }?.timestamp
                    .let { ts -> binding.includeBottomSheet.textUpdateTime.text }
            }
        }
    }

    private fun initGeofenceViewMode(){
        val geofenceViewModelFactory =
            GeofenceViewModelFactory(requireActivity().application)
        geofenceViewModel =
            ViewModelProvider(this, geofenceViewModelFactory).get(GeofenceViewModel::class.java)

        geofenceViewModel.ownerGeofences.observe(viewLifecycleOwner) { fences ->
            setGeofenceSettingUI(fences)
        }
    }

    private fun loadGeofenceChips(geofences: List<GeofenceData>) {

        geofences.forEachIndexed { index, geofence ->
            val chip = layoutInflater.inflate(
                R.layout.custom_chip,
                binding.includeBottomSheet.chipGroupGeofences,
                false
            ) as Chip
            chip.text = geofence.locationName
            chip.id = View.generateViewId()
            chip.isCheckable = true

            // 預設選取第一個 Chip
            val shouldCheck = if (currentFenceId == null) {
                index == 0
            } else {
                geofence.fenceId == currentFenceId
            }

            if (shouldCheck) {
                chip.isChecked = true
                displayGeofenceDetails(geofence)
            }

            chip.setOnClickListener {
                displayGeofenceDetails(geofence)
            }

            chip.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("刪除地理圍欄")
                    .setMessage("確定要刪除「${geofence.locationName}」嗎？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("刪除") { _, _ ->
                        geofenceViewModel.deleteGeofence(geofence.ownerUid, geofence.fenceId,
                            onSuccess = {
                                if (currentFenceId == geofence.fenceId) {
                                    currentFenceId = null
                                    isConfigVisible = true
                                }
                                Toast.makeText(
                                    requireContext(),
                                    "地理圍欄刪除成功",
                                    Toast.LENGTH_SHORT
                                ).show()
                                geofenceViewModel.loadGeofencesSetByMe(geofence.ownerUid)
                            },
                            onFailure = { msg ->
                                Toast.makeText(
                                    requireContext(),
                                    "地理圍欄刪除失敗: $msg",
                                    Toast.LENGTH_SHORT
                                ).show()
                            })
                    }
                    .show()
                true
            }

            binding.includeBottomSheet.chipGroupGeofences.addView(chip)
        }
        // 在最後新增 "+" Chip
        val addChip = layoutInflater.inflate(
            R.layout.custom_chip,
            binding.includeBottomSheet.chipGroupGeofences,
            false
        ) as Chip
        addChip.text = "+"
        addChip.id = View.generateViewId()
        addChip.isCheckable = false  // 不需要選取狀態
        addChip.setChipIconResource(R.drawable.ic_add)  // 可搭配icon（可選）
        addChip.setOnClickListener {
            createNewGeofence()
        }
        binding.includeBottomSheet.chipGroupGeofences.addView(addChip)
    }

    private fun setGeofenceSettingUI(geofences: List<GeofenceData>) {
        // 先清掉前一次的 Chip
        binding.includeBottomSheet.chipGroupGeofences.removeAllViews()

        // 如果清單空的 → 一律顯示「設定」按鈕，按下才進入新增
        if (geofences.isEmpty()) {
            binding.includeBottomSheet.layoutGeofenceConfig.visibility = View.GONE
            binding.includeBottomSheet.btnSetGeofence.apply {
                visibility = View.VISIBLE
                text = "設定 Geofence"
                setOnClickListener {
                    isConfigVisible = true
                    createNewGeofence()
                }
            }
            return
        }

        // 清單非空：如果還沒按過設定按鈕，也沒選過任何 fence → 只顯示設定按鈕
        if (!isConfigVisible && currentFenceId == null) {
            binding.includeBottomSheet.layoutGeofenceConfig.visibility = View.GONE
            binding.includeBottomSheet.btnSetGeofence.apply {
                visibility = View.VISIBLE
                text = "設定 Geofence"
                setOnClickListener {
                    isConfigVisible = true
                    setGeofenceSettingUI(geofences)  // 再次進入這個方法就會走下面那支
                }
            }
            return
        }

        // 到這裡表示「已進入設定模式」(isConfigVisible) or 「正在編輯現有 fence」(currentFenceId!=null)
        binding.includeBottomSheet.btnSetGeofence.visibility = View.GONE
        binding.includeBottomSheet.layoutGeofenceConfig.visibility = View.VISIBLE
        loadGeofenceChips(geofences)
        // 重置這個旗標，留給下次判斷用
        isConfigVisible = false
    }

    private fun createNewGeofence() {
        binding.includeBottomSheet.layoutGeofenceConfig.visibility = View.VISIBLE
        binding.includeBottomSheet.btnSetGeofence.visibility = View.GONE

        isConfigVisible = true
        savedGeofenceData = null
        currentFenceId = null

        binding.includeBottomSheet.etGeofenceLocationName.setText("")
        binding.includeBottomSheet.tvLatLng.text = "緯度: -- , 經度: --"
        binding.includeBottomSheet.sliderRadius.value = DEFAULT_RADIUS
        binding.includeBottomSheet.tvGeofenceRadius.text = "${DEFAULT_RADIUS.toInt()} m"
        binding.includeBottomSheet.chipEnter.isChecked = true
        binding.includeBottomSheet.chipExit.isChecked = true

        binding.includeBottomSheet.btnEditGeofence.performClick()

        Toast.makeText(requireContext(), "請設定新 Geofence 的位置", Toast.LENGTH_SHORT).show()
        updateMapPaddingToBottomSheet()
    }

    private fun updateMapPaddingToBottomSheet() {
        binding.includeBottomSheet.bottomSheet.post {
            googleMap.setPadding(0, 0, 0, binding.includeBottomSheet.bottomSheet.height)
        }
    }

    private fun displayGeofenceDetails(geofence: GeofenceData) {
        currentFenceId = geofence.fenceId

        savedGeofenceData = geofence
        binding.includeBottomSheet.etGeofenceLocationName.setText(geofence.locationName)
        binding.includeBottomSheet.tvLatLng.text =
            "緯度: %.5f, 經度: %.5f".format(geofence.latitude, geofence.longitude)
        binding.includeBottomSheet.sliderRadius.value = geofence.radius
        binding.includeBottomSheet.tvGeofenceRadius.text = "${geofence.radius.toInt()} m"

        // 更新Chip狀態
        binding.includeBottomSheet.chipEnter.isChecked = geofence.transition.contains("enter")
        binding.includeBottomSheet.chipExit.isChecked = geofence.transition.contains("exit")

        addOrResetGeofence(LatLng(geofence.latitude, geofence.longitude), geofence.radius)
    }

    private fun adjustMapPadding(
        sheet: View,
        slideOffset: Float = 1f // collapse 時 slideOffset=0, expand 時=1
    ) {
        val bottomPadding = (sheet.height * slideOffset).toInt()
        googleMap.setPadding(0, 0, 0, bottomPadding)
    }

    private fun convertRelativeUpdateTime(timestamp: Timestamp?): String {
        if (timestamp == null) {
            return "更新時間未知"
        }
        // 轉成毫秒
        val timeMillis = timestamp.toDate().time
        // 產生「5 分鐘前」「2 小時前」之類的字串
        val relative = DateUtils.getRelativeTimeSpanString(
            timeMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        return "更新於 $relative"
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

                Log.d("FCM", "Token:" + token)

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
        currentSharedUserUid = sharedUser.uid
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        binding.includeBottomSheet.textUpdateTime.text = "更新時間未知"

        if (sharedUser.status == "accepted") {
            val marker = markerMap[sharedUser.uid]
            if (marker != null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(marker.position, 17f)
                    )
                }, 200)
            }
            //只有accepted的才可以設定geofence
            geofenceViewModel.loadGeofencesSetByMe(sharedUser.uid)
            binding.includeBottomSheet.chipGroupGeofences.removeAllViews()
            binding.includeBottomSheet.layoutGeofenceConfig.visibility = View.GONE
            binding.includeBottomSheet.btnSetGeofence.visibility = View.VISIBLE
            binding.includeBottomSheet.textUpdateTime.visibility = View.VISIBLE
            mapViewModel.sharedLocations.value
                ?.firstOrNull { it.uid == sharedUser.uid }
                ?.timestamp
                .let { ts -> binding.includeBottomSheet.textUpdateTime.text = convertRelativeUpdateTime(ts) }
        } else {
            binding.includeBottomSheet.chipGroupGeofences.removeAllViews()
            binding.includeBottomSheet.layoutGeofenceConfig.visibility = View.GONE
            binding.includeBottomSheet.btnSetGeofence.visibility = View.GONE
            binding.includeBottomSheet.textUpdateTime.visibility = View.GONE
        }

        binding.includeBottomSheet.apply {
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

            btnSave.setOnClickListener {
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
                    fenceId = savedGeofenceData?.fenceId,
                    ownerUid = sharedUser.uid,
                    latitude = center.latitude,
                    longitude = center.longitude,
                    radius = radius,
                    locationName = nameInput,
                    transition = transitions,
                    onSuccess = { returnedFenceId ->
                        currentFenceId = returnedFenceId
                        Toast.makeText(requireContext(), "地理圍欄儲存成功", Toast.LENGTH_SHORT)
                            .show()
                        geofenceViewModel.loadGeofencesSetByMe(sharedUser.uid)
                    },
                    onFailure = { msg ->
                        Toast.makeText(
                            requireContext(),
                            "地理圍欄儲存失敗: $msg",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            btnEditGeofence.setOnClickListener {
                val marker = markerMap[sharedUser.uid]
                val center = savedGeofenceData
                    ?.let { LatLng(it.latitude, it.longitude) }
                    ?: marker?.position
                val radius = savedGeofenceData?.radius ?: DEFAULT_RADIUS
                if (center != null) {
                    addOrResetGeofence(center, radius)
                    tvLatLng.text =
                        "Lat: %.5f, Lng: %.5f".format(center.latitude, center.longitude)
                }
            }

            sliderRadius.addOnChangeListener { _, value, _ ->
                updateCircleRadius(value.toDouble())
            }
        }
    }

    private val geofenceDragListener = object : GoogleMap.OnMarkerDragListener {
        override fun onMarkerDragStart(marker: Marker) = Unit
        override fun onMarkerDrag(marker: Marker) {
            if (marker == geofenceMarker) {
                geofenceCircle?.center = marker.position
                binding.includeBottomSheet.tvLatLng.text =
                    "緯度: %.5f, 經度: %.5f".format(
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
        binding.includeBottomSheet.tvGeofenceRadius.text = "${radius} m"
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

        googleMap.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
        googleMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.custom_map_info_window, null)
                val ivAvatar = view.findViewById<ImageView>(R.id.ivAvatar)
                val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
                val tvSnippet = view.findViewById<TextView>(R.id.tvSnippet)

                tvTitle.text = marker.title
                tvSnippet.text = marker.snippet

                Log.d("20250517",marker.title+" , "+marker.snippet)

                (marker.tag as? String)?.let { url ->
                    Glide.with(view)
                        .load(url)
                        .circleCrop()
                        .into(ivAvatar)
                }
                return view
            }
        })
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
    private fun updateOrAddMarker(
        displayName: String,
        userId: String,
        latLng: LatLng,
        photoUrl: String?,
        timestamp: Timestamp?
    ) {
        // 產生相對時間字串
        val snippetText = convertRelativeUpdateTime(timestamp)

        // 準備 Glide Request：circleCrop + asBitmap
        val glideRequest = Glide.with(this)
            .asBitmap()
            .circleCrop()

        // 根據 photoUrl 決定 load 什麼
        if (photoUrl.isNullOrEmpty()) {
            glideRequest.load(R.drawable.ic_default_avatar)
        } else {
            glideRequest.load(photoUrl)
        }.into(object : CustomTarget<Bitmap>(60, 60) {
            override fun onResourceReady(
                bitmap: Bitmap,
                transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
            ) {

                val isOffline = timestamp?.let {
                    System.currentTimeMillis() - it.toDate().time > 60 * 60 * 1000
                } ?: false

                val markerBmp = getMarkerBitmapFromView(bitmap,isOffline)
                val icon = BitmapDescriptorFactory.fromBitmap(markerBmp)

                val existing = markerMap[userId]
                if (existing != null) {
                    existing.apply {
                        position = latLng
                        setIcon(icon)
                        snippet = snippetText
                        tag     = photoUrl   // 可以是 null，InfoWindowAdapter 再判斷
                    }
                } else {
                    val marker = googleMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("$displayName")
                            .snippet(snippetText)
                            .icon(icon)
                    )
                    marker?.apply {
                        tag = photoUrl
                        markerMap[userId] = this
                    }
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
        })
    }

    private fun getMarkerBitmapFromView(photo: Bitmap,isOffline: Boolean): Bitmap {
        // Inflate
        var layout_id: Int? = null
        if(isOffline){
            layout_id = R.layout.marker_layout_offline
        }else{
            layout_id = R.layout.marker_layout
        }
        val view = LayoutInflater.from(requireContext())
            .inflate(layout_id, null, false)
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
