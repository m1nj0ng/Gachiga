package com.example.gachiga.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.example.gachiga.R
import com.example.gachiga.network.Place
import com.example.gachiga.network.RetrofitInstance
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSelectionScreen(
    // ★ [추가] 초기 진입 좌표 및 이름 (돋보기 기능용)
    initialLat: Double = 0.0,
    initialLng: Double = 0.0,
    initialPlaceName: String? = null,

    onLocationSelected: (String, LatLng) -> Unit,
    onCancel: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Place>>(emptyList()) }

    // 하나의 통합 선택 상태
    data class SelectedLocation(
        val name: String,
        val address: String?,
        val latLng: LatLng
    )

    var selectedLocation by remember { mutableStateOf<SelectedLocation?>(null) }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val KAKAO_API_KEY = "KakaoAK 7544546b4955f1a8476537614a2a74bf"

    // -----------------------------------------------------------
    // 공통: 지도에 핀 찍는 함수
    // -----------------------------------------------------------
    fun addMarkerToMap(position: LatLng) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return

        // 이전 라벨 제거
        labelManager.layer?.removeAll()

        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(R.drawable.ic_map_pin)
            )
        )
        val options = LabelOptions.from(position).setStyles(styles)
        labelManager.layer?.addLabel(options)
    }

    // -----------------------------------------------------------
    // [Function] 키워드 검색
    // -----------------------------------------------------------
    fun performSearch() {
        if (searchQuery.isNotBlank()) {
            coroutineScope.launch {
                try {
                    val response = RetrofitInstance.api.searchByKeyword(KAKAO_API_KEY, searchQuery)
                    searchResults = response.documents
                } catch (e: Exception) {
                    Log.e("MapSelectionScreen", "API Error: ${e.message}")
                    Toast.makeText(context, "검색 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // -----------------------------------------------------------
    // [Function] 현재 위치 찾기 및 주소 변환 (역지오코딩)
    // -----------------------------------------------------------
    @SuppressLint("MissingPermission")
    fun getCurrentLocationAndSelect() {
        coroutineScope.launch {
            try {
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()

                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude

                    val response = RetrofitInstance.api.coord2address(
                        apiKey = KAKAO_API_KEY,
                        x = lng.toString(),
                        y = lat.toString()
                    )

                    val document = response.documents.firstOrNull()
                    val addressName = document?.roadAddress?.addressName
                        ?: document?.address?.addressName
                        ?: "현재 위치"

                    val latLng = LatLng.from(lat, lng)

                    selectedLocation = SelectedLocation(
                        name = addressName,
                        address = addressName,
                        latLng = latLng
                    )
                    addMarkerToMap(latLng)
                    kakaoMap?.moveCamera(
                        CameraUpdateFactory.newCenterPosition(latLng, 16)
                    )

                    searchResults = emptyList()
                } else {
                    Toast.makeText(context, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MapSelection", "Location Error", e)
                Toast.makeText(context, "위치 확인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        if (isGranted) {
            getCurrentLocationAndSelect()
        } else {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // -----------------------------------------------------------
    // [UI] 화면 구성
    // -----------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("위치 검색 및 선택") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                )
        ) {
            // 상단 검색 영역
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("장소, 주소 검색") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    trailingIcon = {
                        IconButton(onClick = { performSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "검색")
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            getCurrentLocationAndSelect()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "현재 위치로 설정")
                }
            }

            // 지도와 검색 결과 영역
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        MapView(context).apply {
                            this.start(object : MapLifeCycleCallback() {
                                override fun onMapDestroy() {}
                                override fun onMapError(error: Exception) {
                                    Log.e("GachigaMap", "Map Error: $error")
                                }
                            }, object : KakaoMapReadyCallback() {
                                override fun onMapReady(map: KakaoMap) {
                                    kakaoMap = map

                                    // ★ [핵심] 초기 좌표가 전달되었다면(돋보기 클릭) 바로 이동 및 핀 찍기
                                    if (initialLat != 0.0 && initialLng != 0.0) {
                                        val initPos = LatLng.from(initialLat, initialLng)
                                        val initName = initialPlaceName ?: "추천 장소"

                                        // 1. 카메라 이동
                                        map.moveCamera(CameraUpdateFactory.newCenterPosition(initPos, 16))

                                        // 2. 마커 찍기 (Helper 함수 사용)
                                        // 주의: addMarkerToMap 함수는 state인 'kakaoMap'을 사용하므로,
                                        // 여기서 state 업데이트가 반영되기 전일 수 있어 map 객체로 직접 라벨 추가
                                        val labelManager = map.labelManager
                                        val styles = labelManager?.addLabelStyles(
                                            LabelStyles.from(LabelStyle.from(R.drawable.ic_map_pin))
                                        )
                                        val options = LabelOptions.from(initPos).setStyles(styles)
                                        labelManager?.layer?.addLabel(options)

                                        // 3. 하단 카드 띄우기
                                        selectedLocation = SelectedLocation(
                                            name = initName,
                                            address = "추천된 위치입니다", // 주소는 모를 수 있으니 간단히
                                            latLng = initPos
                                        )
                                    }

                                    // 지도 클릭 시: 역지오코딩 + 선택 카드 표시
                                    map.setOnMapClickListener { _, latLng, _, _ ->
                                        coroutineScope.launch {
                                            try {
                                                val res = RetrofitInstance.api.coord2address(
                                                    apiKey = KAKAO_API_KEY,
                                                    x = latLng.longitude.toString(),
                                                    y = latLng.latitude.toString()
                                                )
                                                val doc = res.documents.firstOrNull()
                                                val addr = doc?.roadAddress?.addressName
                                                    ?: doc?.address?.addressName
                                                    ?: "선택한 위치"

                                                selectedLocation = SelectedLocation(
                                                    name = addr,
                                                    address = addr,
                                                    latLng = latLng
                                                )
                                                addMarkerToMap(latLng)
                                                searchResults = emptyList()
                                            } catch (e: Exception) {
                                                Log.e("MapSelection", "Reverse geocoding error", e)
                                            }
                                        }
                                    }
                                }

                                override fun getZoomLevel(): Int = 16
                            })
                        }
                    },
                    update = {},
                    modifier = Modifier.fillMaxSize()
                )

                // 검색 결과 목록
                if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .windowInsetsPadding(WindowInsets.ime)
                    ) {
                        items(searchResults) { place ->
                            ListItem(
                                headlineContent = {
                                    Text(place.placeName, fontWeight = FontWeight.Bold)
                                },
                                supportingContent = {
                                    Text(place.roadAddressName.ifEmpty { place.addressName }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    val position = LatLng.from(
                                        place.latitude.toDouble(),
                                        place.longitude.toDouble()
                                    )

                                    selectedLocation = SelectedLocation(
                                        name = place.placeName,
                                        address = place.roadAddressName.ifBlank { place.addressName },
                                        latLng = position
                                    )

                                    kakaoMap?.moveCamera(
                                        CameraUpdateFactory.newCenterPosition(position, 16)
                                    )
                                    addMarkerToMap(position)

                                    searchResults = emptyList()
                                }
                            )
                            Divider()
                        }
                    }
                }

                // 🔻 아래 카드: 선택된 장소 정보 + 설정 버튼
                selectedLocation?.let { sel ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                sel.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            sel.address?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    onLocationSelected(sel.name, sel.latLng)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("이 위치로 설정")
                            }
                        }
                    }
                }
            }
        }
    }
}