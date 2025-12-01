package com.example.gachiga.ui.room

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gachiga.data.RoomDetail
import com.example.gachiga.data.RoomMember
import com.example.gachiga.data.User
import com.example.gachiga.navigation.AppDestinations
import com.example.gachiga.ui.input.InfoRow
import com.example.gachiga.ui.input.TimePickerDialog
import com.example.gachiga.ui.input.TransportButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    navController: NavController,
    loggedInUser: User,
    roomDetail: RoomDetail,
    onStateChange: (RoomDetail) -> Unit,
    onCalculate: () -> Unit
) {
    val isHost = roomDetail.members.find { it.user.id == loggedInUser.id }?.isHost ?: false
    val allMembersReady = roomDetail.members.all { it.isReady }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("약속 방 상세정보") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 초대 코드 섹션
            InvitationCodeSection(roomDetail.invitationCode)

            // 공통 정보 섹션 (방장만 수정 가능)
            CommonInfoSection(
                navController = navController,
                isHost = isHost,
                roomDetail = roomDetail,
                onStateChange = onStateChange
            )
            Divider()

            // 멤버 목록
            Text("멤버", style = MaterialTheme.typography.titleLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(roomDetail.members) { member ->
                    MemberStatusCard(
                        isSelf = (member.user.id == loggedInUser.id),
                        member = member,
                        onStateChange = { updatedMember ->
                            val updatedList = roomDetail.members.map {
                                if (it.user.id == updatedMember.user.id) updatedMember else it
                            }
                            onStateChange(roomDetail.copy(members = updatedList))
                        },
                        navController = navController,
                        roomId = roomDetail.roomId
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 방장 전용 계산 버튼
            if (isHost) {
                Button(
                    onClick = { onCalculate() },
                    enabled = allMembersReady, // 모든 멤버가 준비 완료 상태일 때만 활성화
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("중간지점 계산하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// `RoomDetailScreen`에서만 사용하는 작은 Composable들

@Composable
private fun InvitationCodeSection(code: String) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("초대 코드", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(
                onClick = {
                    val clip = ClipData.newPlainText("초대 코드", code)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "초대 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, "코드 복사")
            }
        }
    }
}

@Composable
private fun CommonInfoSection(
    navController: NavController,
    isHost: Boolean,
    roomDetail: RoomDetail,
    onStateChange: (RoomDetail) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoRow(icon = Icons.Default.Flag, title = "목적지") {
            Button(
                onClick = { navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/destination/-1?roomId=${roomDetail.roomId}") },
                enabled = isHost
            ) {
                Text(roomDetail.destination)
            }
        }
        InfoRow(icon = Icons.Default.Schedule, title = "도착 시간") {
            Button(
                onClick = { showTimePicker = true },
                enabled = isHost
            ) {
                Text(roomDetail.arrivalTime)
            }
        }
    }

    if (showTimePicker) {
        val initialHour = roomDetail.arrivalTime.substringBefore(":").toIntOrNull() ?: 12
        val initialMinute = roomDetail.arrivalTime.substringAfter(":").toIntOrNull() ?: 0

        // 새로운 TimePickerDialog 호출
        TimePickerDialog(
            initialHour = initialHour,
            initialMinute = initialMinute,
            onTimeSelected = { hour, minute ->
                val formattedTime = String.format("%02d:%02d", hour, minute)
                onStateChange(roomDetail.copy(arrivalTime = formattedTime))
            },
            onDismiss = {
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun MemberStatusCard(
    isSelf: Boolean,
    member: RoomMember,
    onStateChange: (RoomMember) -> Unit,
    navController: NavController,
    roomId: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelf) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.user.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (member.isHost) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Star, "방장", tint = Color(0xFFFFC107))
                }
                Spacer(modifier = Modifier.weight(1f))
                if (member.isReady) {
                    Icon(Icons.Default.CheckCircle, "준비 완료", tint = Color.Green)
                } else {
                    Icon(Icons.Default.RadioButtonUnchecked, "준비 중")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 출발지 설정
            InfoRow(icon = Icons.Default.MyLocation, title = "출발지") {
                Button(
                    onClick = {
                        // 본인 카드일 때만 작동
                        if (isSelf) {
                            navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/startPoint/-1?roomId=${roomId}")
                        }
                    },
                    enabled = isSelf // 본인만 활성화
                ) {
                    Text(member.startPoint)
                }
            }

            // 교통수단 설정
            InfoRow(icon = Icons.Default.DirectionsCar, title = "교통수단") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // "대중교통" 버튼
                    TransportButton(
                        text = "대중교통",
                        isSelected = member.travelMode == com.example.gachiga.data.TravelMode.TRANSIT,
                        onClick = {
                            if (isSelf) {
                                onStateChange(
                                    member.copy(
                                        travelMode = com.example.gachiga.data.TravelMode.TRANSIT,
                                        isReady = false
                                    )
                                )
                            }
                        }
                    )
                    // "자차" 버튼
                    TransportButton(
                        text = "자차",
                        isSelected = member.travelMode == com.example.gachiga.data.TravelMode.CAR,
                        onClick = {
                            if (isSelf) {
                                onStateChange(
                                    member.copy(
                                        travelMode = com.example.gachiga.data.TravelMode.CAR,
                                        isReady = false
                                    )
                                )
                            }
                        }
                    )
                    // "도보" 버튼 추가
                    TransportButton(
                        text = "도보",
                        isSelected = member.travelMode == com.example.gachiga.data.TravelMode.WALK,
                        onClick = {
                            if (isSelf) {
                                onStateChange(
                                    member.copy(
                                        travelMode = com.example.gachiga.data.TravelMode.WALK,
                                        isReady = false
                                    )
                                )
                            }
                        }
                    )
                }
            }

            // 준비 완료 버튼 (본인만 보임)
            if (isSelf && !member.isReady) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onStateChange(member.copy(isReady = true)) },
                    enabled = member.startPoint != "미설정",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("준비 완료")
                }
            }
        }
    }
}
