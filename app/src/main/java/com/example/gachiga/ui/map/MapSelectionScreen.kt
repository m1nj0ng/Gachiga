package com.example.gachiga.ui.map

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gachiga.network.Place
import com.example.gachiga.network.RetrofitInstance
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import kotlinx.coroutines.launch

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

    // 키보드 컨트롤러와 포커스 매니저를 가져옴
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val KAKAO_API_KEY = "KakaoAK 7544546b4955f1a8476537614a2a74bf"

    // 검색을 실행하는 공통 함수
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
            // 검색 실행 후 키보드를 내리고 포커스를 해제
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

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
            // 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                label = { Text("장소, 주소 검색") },
                singleLine = true,
                // 키보드 액션 설정
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    performSearch() // 키보드의 검색 버튼 클릭 시 검색 실행
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        performSearch() // 돋보기 아이콘 클릭 시 검색 실행
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                }
            )

            // 지도와 검색 결과 영역
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        MapView(context).apply {
                            this.start(object : MapLifeCycleCallback() {      // 1. MapLifeCycleCallback이 먼저 오도록
                                override fun onMapDestroy() {}
                                override fun onMapError(error: Exception) {
                                    Log.e("GachigaMap", "Map Error: $error")
                                }
                            }, object : KakaoMapReadyCallback() {         // 2. KakaoMapReadyCallback이 나중에 오도록
                                override fun onMapReady(map: KakaoMap) {
                                    kakaoMap = map
                                    // 지도가 준비되면 지도 클릭 리스너 설정
                                    map.setOnMapClickListener { _, _, _, _ ->
                                        searchResults = emptyList() // 지도 클릭 시 검색 결과 숨김
                                        selectedPlace = null // 선택된 장소도 초기화
                                    }
                                }

                                override fun getZoomLevel(): Int {
                                    return 16 // 초기 줌 레벨
                                }
                            })
                        }
                    },
                    update = {
                        // Composable이 업데이트될 때 필요한 로직 (지금은 비워둠)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 검색 결과 목록
                if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .windowInsetsPadding(WindowInsets.ime) // 키보드 영역만큼 패딩
                    ) {
                        items(searchResults) { place ->
                            ListItem(
                                headlineContent = {
                                    Text(place.placeName, fontWeight = FontWeight.Bold)
                                },
                                supportingContent = { Text(place.roadAddressName.ifEmpty { place.addressName }) },
                                modifier = Modifier.clickable {
                                    selectedPlace = place
                                    val position = LatLng.from(
                                        place.latitude.toDouble(),
                                        place.longitude.toDouble()
                                    )
                                    kakaoMap?.moveCamera(
                                        CameraUpdateFactory.newCenterPosition(
                                            position,
                                            16
                                        )
                                    )
                                    searchResults = emptyList()
                                }
                            )
                            Divider()
                        }
                    }
                }

                // 최종 선택 버튼 (장소가 선택되었을 때만 보임)
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