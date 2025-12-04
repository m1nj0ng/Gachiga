package com.example.gachiga.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.gachiga.R
import com.example.gachiga.data.RoomMember
import com.example.gachiga.data.SuggestedRoute
import com.example.gachiga.data.User
import com.example.gachiga.util.RouteMath
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

/**
 * 로그인 버전의 투표 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteScreen(
    navController: NavController,
    loggedInUser: User,
    members: List<RoomMember>,
    routes: List<SuggestedRoute>,
    isHost: Boolean,
    onVote: (routeId: String, userId: String) -> Unit,
    onVoteComplete: (userId: String) -> Unit,
    onFinalSelect: (routeId: String) -> Unit
) {
    var selectedRouteForDetail by remember { mutableStateOf<SuggestedRoute?>(null) }
    val me = members.find { it.user.id == loggedInUser.id }
    val allMembersVoted = members.all { it.voted }
    val voteCounts = routes.associate { it.id to it.voters.size }
    val maxVote = if (voteCounts.isNotEmpty()) voteCounts.values.max() else 0
    val topRoutes = routes.filter { (voteCounts[it.id] ?: 0) == maxVote && maxVote > 0 }
    val isFinalButtonEnabled =
        maxVote > 0 && (topRoutes.size == 1 || (topRoutes.size > 1 && isHost))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedRouteForDetail == null) "투표하여 중간지점 결정" else "경로 상세 정보",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedRouteForDetail != null) {
                            // 지도 화면일 경우 -> 목록화면으로 (상세보기 해제)
                            selectedRouteForDetail = null
                        } else {
                            // 목록 화면일 경우 -> 이전 화면(RoomDetailScreen)으로
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 상단 컨텐츠 영역 (목록 또는 지도)
            Box(modifier = Modifier.weight(1f))
            {
                // 선택된 경로에 따라 다른 화면을 보여줌
                if (selectedRouteForDetail == null) {
                    // --- 투표 목록 화면 ---
                    VoteListContent(
                        members = members,
                        routes = routes,
                        loggedInUser = loggedInUser,
                        allMembersVoted = allMembersVoted,
                        topRoutes = topRoutes,
                        isHost = isHost,
                        onVote = onVote,
                        onFinalSelect = onFinalSelect,
                        onShowDetail = { route -> selectedRouteForDetail = route } // 상세 보기 콜백
                    )
                } else {                    // --- 경로 상세 및 지도 화면 ---
                    RouteDetailMapContent(route = selectedRouteForDetail!!)
                }
            }
            // 하단 버튼 영역
            if (me != null && !me.voted) {
                // "투표 완료" 버튼 (아직 투표 완료 안 한 사람에게만 보임)
                Button(
                    onClick = { onVoteComplete(loggedInUser.id) },
                    enabled = me.voted.not(), // 아직 투표 완료 안했을 때만 활성화
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text("투표 완료")
                }
            } else if (allMembersVoted && topRoutes.size == 1 && isHost) {
                // "이 장소로 확정하기" 버튼 (모두 투표 완료했고, 단독 1위일 때)
                Button(
                    onClick = { onFinalSelect(topRoutes.first().id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text("이 장소로 확정하기")
                }
            }
        }
    }
}

/**
 * 투표 목록을 보여주는 Composable
 */
@Composable
private fun VoteListContent(
    members: List<RoomMember>,
    routes: List<SuggestedRoute>,
    loggedInUser: User,
    allMembersVoted: Boolean,
    topRoutes: List<SuggestedRoute>,
    isHost: Boolean,
    onVote: (String, String) -> Unit,
    onFinalSelect: (String) -> Unit,
    onShowDetail: (SuggestedRoute) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        VoteStatusSection(members = members)
        Divider()
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            itemsIndexed(routes) { index, route ->
                // 내 정보 찾기
                val myInfo = members.find { it.user.id == loggedInUser.id }
                VoteCard(
                    route = route,
                    rank = index + 1,
                    isVotedByMe = loggedInUser.id in route.voters,
                    onVote = { onVote(route.id, loggedInUser.id) },
                    showFinalSelectButton = allMembersVoted && topRoutes.size > 1 && isHost,
                    onFinalSelect = { onFinalSelect(route.id) },
                    isTopRoute = route in topRoutes && allMembersVoted,
                    canVote = !allMembersVoted,
                    onShowDetail = { onShowDetail(route) },
                    myMemberInfo = myInfo // ★ 전달
                )
            }
        }
    }
}

/**
 * 선택된 경로의 상세 정보와 지도를 보여주는 Composable (ResultScreen의 것을 재활용)
 */
@Composable
private fun RouteDetailMapContent(route: SuggestedRoute) {
    // ... (이전 답변의 RouteDetailContent와 코드가 동일)
    val position = LatLng.from(route.latitude, route.longitude)
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                this.start(object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {}
                    override fun onMapError(error: Exception) {}
                }, object : KakaoMapReadyCallback() {
                    override fun onMapReady(kakaoMap: KakaoMap) {
                        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(position, 16))
                        val labelStyles =
                            kakaoMap.labelManager?.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.ic_map_pin)))
                        val labelOptions = LabelOptions.from(position).setStyles(labelStyles)
                        kakaoMap.labelManager?.layer?.addLabel(labelOptions)
                    }
                })
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun VoteCard(
    route: SuggestedRoute,
    rank: Int,
    isVotedByMe: Boolean,
    onVote: () -> Unit,
    onShowDetail: () -> Unit,
    showFinalSelectButton: Boolean,
    onFinalSelect: () -> Unit,
    isTopRoute: Boolean,
    canVote: Boolean,

    // ★ [추가] 거리 계산을 위해 내 정보를 받습니다.
    myMemberInfo: RoomMember?
) {
    // ★ [로직 추가] 내 위치에서 후보지까지의 직선 거리 계산
    val myDistanceStr = remember(route, myMemberInfo) {
        if (myMemberInfo?.x != null && myMemberInfo.y != null) {
            val start = com.kakao.vectormap.LatLng.from(myMemberInfo.y!!, myMemberInfo.x!!)
            val end = com.kakao.vectormap.LatLng.from(route.latitude, route.longitude)

            // RouteMath를 활용해 거리(m) 계산
            val distMeters = com.example.gachiga.util.RouteMath.haversineMeters(start, end)

            // 1km 이상이면 km 단위, 아니면 m 단위 표시
            if (distMeters >= 1000) String.format("%.1fkm", distMeters / 1000)
            else "${distMeters.toInt()}m"
        } else {
            "-"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTopRoute) 4.dp else 2.dp),
        border = if (isVotedByMe || (isTopRoute && showFinalSelectButton)) BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // [상단] 순위, 이름, 투표 버튼
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTopRoute) Icon(Icons.Default.Star, "1등", tint = Color(0xFFFFC107))
                    Text(
                        route.placeName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onVote,
                    enabled = canVote
                ) {
                    Icon(Icons.Default.HowToVote, "투표", Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isVotedByMe) "취소" else "투표")
                }
            }

            Spacer(Modifier.height(4.dp))

            // [중단] 득표수
            Text(
                "총 ${route.voters.size}표",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // [하단] 거리 정보, 카테고리, 지도 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. 내 위치로부터의 거리
                Column {
                    Text("내 위치에서", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = "$myDistanceStr",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 2. 장소 카테고리 (예: 카페, 지하철역) - totalFee 필드 활용
                Text(
                    text = route.totalFee,
                    style = MaterialTheme.typography.bodyMedium
                )

                // 3. 지도 보기 아이콘
                IconButton(onClick = onShowDetail) {
                    Icon(Icons.Default.Search, "지도 보기")
                }
            }

            // [하단] 방장 전용 확정 버튼
            if (showFinalSelectButton) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onFinalSelect, modifier = Modifier.align(Alignment.End)) {
                    Text("이 장소로 확정")
                }
            }
        }
    }
}

/**
 * 멤버들의 투표 완료 상태를 보여주는 섹션
 */
@Composable
private fun VoteStatusSection(members: List<RoomMember>) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "투표 현황 (${members.count { it.voted }} / ${members.size})",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "완료",
                        tint = if (member.voted) Color.Green else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        member.user.nickname,
                        color = if (member.voted) Color.Unspecified else Color.Gray
                    )
                }
            }
        }
    }
}