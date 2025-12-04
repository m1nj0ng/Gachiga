package com.example.gachiga.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.example.gachiga.navigation.AppDestinations
import com.example.gachiga.data.GachigaState
import com.example.gachiga.data.Member
import com.example.gachiga.data.CarRouteOption
import com.example.gachiga.data.PublicTransitOption
import com.example.gachiga.data.TravelMode


val USER_COLORS = listOf(
    0xFF1976D2.toInt(), // 파랑
    0xFFFF9800.toInt(), // 주황
    0xFF388E3C.toInt(), // 초록
    0xFF7B1FA2.toInt(), // 보라
    0xFF0097A7.toInt(), // 청록
    0xFFC2185B.toInt(), // 자주
    0xFF5D4037.toInt(), // 갈색
    0xFFFFC107.toInt()  // 노랑
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(
    navController: NavController,
    gachigaState: GachigaState,
    onStateChange: (GachigaState) -> Unit
) {
    val scrollState = rememberScrollState()
    val allStartPointsSet = gachigaState.members.all { it.startPoint != "미설정" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("중간지점 찾기", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CommonInfoSection(
                navController = navController,
                state = gachigaState,
                onStateChange = onStateChange
            )
            Divider()
            MemberInfoSection(
                navController = navController,
                members = gachigaState.members,
                onStateChange = { updatedMembers ->
                    onStateChange(gachigaState.copy(members = updatedMembers))
                }
            )
            Button(
                onClick = {
                    navController.navigate(AppDestinations.RESULT_SCREEN)
                },
                enabled = allStartPointsSet && gachigaState.destination != "미설정",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("중간지점 찾기!", fontSize = 18.sp)
            }
        }
    }
}

// 공통 정보 섹션 (목적지, 도착시간)
@Composable
fun CommonInfoSection(
    navController: NavController,
    state: GachigaState,
    onStateChange: (GachigaState) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("공통 정보", style = MaterialTheme.typography.titleLarge)
        InfoRow(icon = Icons.Default.Flag, title = "목적지") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/destination/-1")
                }) {
                    Text(state.destination)
                }

                // ★ [추가] 삭제 버튼 (목적지가 설정되어 있을 때만 표시)
                if (state.destination != "미설정") {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            // 초기화
                            onStateChange(state.copy(destination = "미설정", destX = null, destY = null))
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "목적지 삭제", tint = Color.Gray)
                    }
                }
            }
        }
        InfoRow(icon = Icons.Default.Schedule, title = "도착 시간") {
            Button(onClick = { showTimePicker = true }) {
                Text(state.arrivalTime)
            }
        }
    }

    if (showTimePicker) {
        val initialHour = state.arrivalTime.substringBefore(":").toIntOrNull() ?: 12
        val initialMinute = state.arrivalTime.substringAfter(":").toIntOrNull() ?: 0

        TimePickerDialog(
            initialHour = initialHour,
            initialMinute = initialMinute,
            onTimeSelected = { hour, minute ->
                val formattedTime = String.format("%02d:%02d", hour, minute)
                onStateChange(state.copy(arrivalTime = formattedTime))
            },
            onDismiss = {
                showTimePicker = false
            }
        )
    }
}

// 멤버별 정보 섹션
@Composable
fun MemberInfoSection(
    navController: NavController,
    members: List<Member>,
    onStateChange: (List<Member>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("멤버별 정보", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                // ★ [수정] 순서에 맞춰 색상을 가져오는 로직 추가
                val nextColor = USER_COLORS[members.size % USER_COLORS.size]

                val newMember = Member(
                    id = members.size + 1,
                    name = "멤버 ${members.size + 1}",
                    color = nextColor // ★ 진짜 색상 부여
                )
                onStateChange(members + newMember)
            }) {
                Icon(Icons.Default.Add, contentDescription = "멤버 추가")
                Text("멤버 추가")
            }
        }
        members.forEachIndexed { index, member ->
            MemberCard(
                navController = navController,
                member = member,
                memberIndex = index,
                onMemberChange = { updatedMember ->
                    val updatedList = members.toMutableList()
                    updatedList[index] = updatedMember
                    onStateChange(updatedList)
                },
                onRemoveMember = {
                    if (members.size > 1) {
                        onStateChange(members.filter { it != member })
                    }
                }
            )
        }
    }
}

// 각 멤버의 정보 카드
@Composable
fun MemberCard(
    navController: NavController,
    member: Member,
    memberIndex: Int,
    onMemberChange: (Member) -> Unit,
    onRemoveMember: () -> Unit
) {
    var carOptionMenuExpanded by remember { mutableStateOf(false) }
    var publicOptionMenuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (memberIndex > 0) {
                    IconButton(onClick = onRemoveMember) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "멤버 삭제")
                    }
                }
            }
            // ★ [수정] 출발지 설정 영역
            InfoRow(icon = Icons.Default.MyLocation, title = "출발지") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/startPoint/$memberIndex")
                    }) {
                        Text(member.startPoint)
                    }

                    // ★ [추가] 삭제 버튼 (출발지가 설정되어 있을 때만 표시)
                    if (member.startPoint != "미설정") {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                // 초기화
                                onMemberChange(member.copy(startPoint = "미설정", x = null, y = null))
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "출발지 삭제", tint = Color.Gray)
                        }
                    }
                }
            }
            InfoRow(icon = Icons.Default.DirectionsBus, title = "교통수단") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    TransportButton(
                        icon = Icons.Default.DirectionsTransit,
                        contentDescription = "대중교통",
                        isSelected = member.mode == TravelMode.TRANSIT,
                        onClick = { onMemberChange(member.copy(mode = TravelMode.TRANSIT)) }
                    )

                    TransportButton(
                        icon = Icons.Default.DirectionsCar,
                        contentDescription = "자동차",
                        isSelected = member.mode == TravelMode.CAR,
                        onClick = { onMemberChange(member.copy(mode = TravelMode.CAR)) }
                    )

                    TransportButton(
                        icon = Icons.Default.DirectionsWalk,
                        contentDescription = "도보",
                        isSelected = member.mode == TravelMode.WALK,
                        onClick = { onMemberChange(member.copy(mode = TravelMode.WALK)) }
                    )
                }
            }

            // ★★★ [수정] 옵션 선택 시 searchOption(숫자)도 함께 업데이트하는 로직 추가 ★★★
            when (member.mode) {
                TravelMode.CAR -> {
                    // --- 자동차 옵션 ---
                    InfoRow(icon = Icons.Default.Tune, title = "경로 옵션") {
                        Box {
                            TextButton(onClick = { carOptionMenuExpanded = true }) {
                                Text(member.carOption.displayName)
                            }
                            DropdownMenu(
                                expanded = carOptionMenuExpanded,
                                onDismissRequest = { carOptionMenuExpanded = false }
                            ) {
                                CarRouteOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            // ★ TMAP 자동차 옵션 매핑
                                            // 0:추천, 1:무료, 2:최소시간, 10:최단거리
                                            val code = when(option) {
                                                CarRouteOption.RECOMMEND -> 0
                                                CarRouteOption.FREE -> 1
                                                CarRouteOption.FASTEST -> 2
                                                CarRouteOption.SHORTEST -> 10
                                            }
                                            // Enum과 Int를 동시에 업데이트
                                            onMemberChange(member.copy(carOption = option, searchOption = code))
                                            carOptionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                TravelMode.TRANSIT -> {
                    // --- 대중교통 옵션 ---
                    InfoRow(icon = Icons.Default.Tune, title = "경로 옵션") {
                        Box {
                            TextButton(onClick = { publicOptionMenuExpanded = true }) {
                                Text(member.publicTransitOption.displayName)
                            }
                            DropdownMenu(
                                expanded = publicOptionMenuExpanded,
                                onDismissRequest = { publicOptionMenuExpanded = false }
                            ) {
                                PublicTransitOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            // ★ TMAP 대중교통 옵션 매핑
                                            // 0:최적, 1:최소환승, 2:최소시간, 3:최소도보
                                            val code = when(option) {
                                                PublicTransitOption.OPTIMAL -> 0
                                                PublicTransitOption.LEAST_TRANSFER -> 1
                                                PublicTransitOption.FASTEST -> 2
                                                PublicTransitOption.LEAST_WALKING -> 3
                                            }
                                            // Enum과 Int를 동시에 업데이트
                                            onMemberChange(member.copy(publicTransitOption = option, searchOption = code))
                                            publicOptionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                TravelMode.WALK -> {
                    // 도보일 때는 옵션 없음
                }
            }
        }
    }
}