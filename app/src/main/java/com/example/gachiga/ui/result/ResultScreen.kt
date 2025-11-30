package com.example.gachiga.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.gachiga.data.SuggestedRoute
import com.example.gachiga.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavController,
    routes: List<SuggestedRoute>
) {
    // 사용자가 선택한 경로의 id를 저장하는 상태 변수
    var routeForDetail by remember { mutableStateOf<SuggestedRoute?>(null) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    val isRouteSelected = selectedRouteId != null

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    if (routeForDetail == null) "추천 중간지점" else "경로 상세 정보",
                    fontWeight = FontWeight.Bold
                )
            }, navigationIcon = {
                IconButton(onClick = {
                    if (routeForDetail != null) {
                        routeForDetail = null // 지도 -> 목록
                    } else {
                        // 목록 화면일 경우 -> 이전 화면(InputScreen)으로
                        navController.navigateUp()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로가기")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 상단 컨텐츠 영역 (목록 또는 지도)
            Box(modifier = Modifier.weight(1f)) { // 남은 공간을 모두 차지
                if (routeForDetail == null) {
                    RouteListContent(
                        routes = routes,
                        selectedRouteId = selectedRouteId, // 현재 선택된 ID 전달
                        onRouteSelected = { routeId ->
                            // 카드를 클릭하면 선택된 ID를 업데이트
                            selectedRouteId = if (selectedRouteId == routeId) null else routeId
                        },
                        onShowDetail = { route ->
                            // 돋보기를 누르면 지도 볼 경로를 업데이트
                            routeForDetail = route
                        }
                    )
                } else {
                    RouteDetailContent(route = routeForDetail!!)
                }
            }

            // 최종 선택 버튼
            Button(
                onClick = { /* TODO: 선택된 경로 정보로 다음 단계 진행 */ },
                // 경로가 하나라도 선택되었을 때만 버튼 활성화
                enabled = selectedRouteId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Text("이 경로로 정하기")
            }
        }
    }
}

/**
 * 경로 목록을 보여주는 Composable
 */
@Composable
private fun RouteListContent(
    routes: List<SuggestedRoute>,
    onRouteSelected: (String) -> Unit,
    selectedRouteId: String?,
    onShowDetail: (SuggestedRoute) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        itemsIndexed(routes) { index, route ->
            RouteCard(
                route = route, rank = index + 1,
                isSelected = (route.id == selectedRouteId),
                onCardClick = { onRouteSelected(route.id) },
                onShowDetail = { onRouteSelected(route.id) })
        }
    }
}

/**
 * 선택된 경로의 상세 정보와 지도를 보여주는 Composable
 */
@Composable
private fun RouteDetailContent(route: SuggestedRoute) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 지도 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.LightGray)
        ) {
            val position = LatLng.from(route.latitude, route.longitude)
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        this.start(object : MapLifeCycleCallback() {
                            override fun onMapDestroy() {}
                            override fun onMapError(error: Exception) {}
                        }, object : KakaoMapReadyCallback() {
                            override fun onMapReady(kakaoMap: KakaoMap) {
                                // 선택된 위치로 카메라 이동 및 마커 표시
                                kakaoMap.moveCamera(
                                    CameraUpdateFactory.newCenterPosition(
                                        position, 16
                                    )
                                )
                                val labelStyles = kakaoMap.labelManager?.addLabelStyles(
                                    LabelStyles.from(LabelStyle.from(R.drawable.ic_map_pin))
                                )
                                val labelOptions =
                                    LabelOptions.from(position).setStyles(labelStyles)
                                kakaoMap.labelManager?.layer?.addLabel(labelOptions)
                            }
                        })
                    }
                }, modifier = Modifier.fillMaxSize()
            )
        }
        // TODO: 여기에 경로 상세 정보를 추가할 수 있습니다. (예: 멤버별 이동 경로)
        // 임시로 장소 이름만 표시
        Text(
            text = route.placeName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
    }
}


@Composable
private fun RouteCard(
    route: SuggestedRoute,
    rank: Int,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onShowDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$rank.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = route.placeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(route.address, style = MaterialTheme.typography.bodyMedium)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("소요시간: ${route.totalTime}")
                Text("비용: ${route.totalFee}")
                IconButton(onClick = onShowDetail) {
                    Icon(Icons.Default.Search, "지도 보기")
                }
            }
        }
    }
}