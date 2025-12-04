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
import com.example.gachiga.network.Place
import com.example.gachiga.network.RetrofitInstance
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSelectionScreen(
    onLocationSelected: (String, LatLng) -> Unit,
    onCancel: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // ★ FusedLocationClient (위치 서비스 객체)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // ★ 임시 API 키 (나중에 BuildConfig로 옮기는 것 추천)
    val KAKAO_API_KEY = "KakaoAK 7544546b4955f1a8476537614a2a74bf"

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
                }
            }
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // -----------------------------------------------------------
    // [Function] 현재 위치 찾기 및 주소 변환 (역지오코딩)
    // -----------------------------------------------------------
    @SuppressLint("MissingPermission") // 권한 체크는 호출 전에 함
    fun getCurrentLocationAndSelect() {
        coroutineScope.launch {
            try {
                // 1. 마지막 위치 가져오기 (없으면 null)
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()

                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude

                    // 2. 좌표 -> 주소 변환 (Kakao API 역지오코딩)
                    val response = RetrofitInstance.api.coord2address(
                        apiKey = KAKAO_API_KEY,
                        x = lng.toString(),
                        y = lat.toString()
                    )

                    // 3. 주소 문자열 추출 (도로명 우선, 없으면 지번)
                    val document = response.documents.firstOrNull()
                    val addressName = document?.roadAddress?.addressName
                        ?: document?.address?.addressName
                        ?: "현재 위치"

                    // 4. 선택 완료 처리 (바로 이전 화면으로 복귀)
                    onLocationSelected(addressName, LatLng.from(lat, lng))

                } else {
                    Toast.makeText(context, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MapSelection", "Location Error", e)
                Toast.makeText(context, "위치 확인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -----------------------------------------------------------
    // [Permission] 권한 요청 런처
    // -----------------------------------------------------------
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        if (isGranted) {
            getCurrentLocationAndSelect() // 권한 허용되면 바로 위치 찾기 실행
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
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
        ) {
            // 상단 검색 영역 (Row로 감싸서 버튼 배치)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 검색창
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f), // 남은 공간 차지
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

                // ★ 2. [추가] 내 위치 찾기 버튼
                IconButton(
                    onClick = {
                        // 권한 체크 및 요청
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
                    modifier = Modifier.size(56.dp) // 높이 맞춤
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
                                    map.setOnMapClickListener { _, _, _, _ ->
                                        searchResults = emptyList()
                                        selectedPlace = null
                                    }
                                }
                                override fun getZoomLevel(): Int { return 16 }
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
                                supportingContent = { Text(place.roadAddressName.ifEmpty { place.addressName }) },
                                modifier = Modifier.clickable {
                                    selectedPlace = place
                                    val position = LatLng.from(place.latitude.toDouble(), place.longitude.toDouble())
                                    kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(position, 16))
                                    searchResults = emptyList()
                                }
                            )
                            Divider()
                        }
                    }
                }

                // 최종 선택 버튼 (장소 선택 시)
                selectedPlace?.let { place ->
                    Button(
                        onClick = {
                            onLocationSelected(
                                place.placeName,
                                LatLng.from(place.latitude.toDouble(), place.longitude.toDouble())
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text("'${place.placeName}'으로 설정")
                    }
                }
            }
        }
    }
}