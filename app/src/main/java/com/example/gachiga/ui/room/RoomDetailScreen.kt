package com.example.gachiga.ui.room

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.example.gachiga.data.CarRouteOption
import com.example.gachiga.data.PublicTransitOption
import com.example.gachiga.data.RoomDetail
import com.example.gachiga.data.RoomMember
import com.example.gachiga.data.TravelMode
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
    onMemberUpdate: (RoomMember) -> Unit,
    onCalculate: () -> Unit,
    onBackAction: () -> Unit
) {
    val isHost = roomDetail.members.find { it.user.id == loggedInUser.id }?.isHost ?: false
    val allMembersReady = roomDetail.members.all { it.isReady }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("약속 방 상세정보") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 초대 코드 섹션
            InvitationCodeSection(
                code = roomDetail.invitationCode,
                inviteLink = roomDetail.inviteLink
            )

            // 카카오톡 초대 버튼
            KakaoInviteButton(code = roomDetail.invitationCode)

            Divider()

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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)) {
                items(roomDetail.members) { member ->
                    MemberStatusCard(
                        isSelf = (member.user.id == loggedInUser.id),
                        member = member,
                        onStateChange = { updatedMember ->
                            onMemberUpdate(updatedMember)
                        },
                        navController = navController,
                        roomId = roomDetail.roomId
                    )
                }
            }

            // 하단 버튼 (방장, 멤버 공통으로 보여주되 방장만 누를 수 있게)
            Button(
                onClick = { onCalculate() },

                // 활성화 조건: (1)방장, (2)모두 준비 완료, (3)목적지 설정됨
                enabled = isHost && allMembersReady && roomDetail.destination != "미설정",

                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                // (선택사항) 비활성화되었을 때 색상 지정 (회색 배경, 진한 회색 글씨)
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.LightGray,
                    disabledContentColor = Color.DarkGray
                )
            ) {
                // 방장인지 아닌지에 따라 문구 변경
                Text(
                    text = if (isHost) "중간지점 계산하기" else "방장이 계산할 때까지 대기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// `RoomDetailScreen`에서만 사용하는 작은 Composable들

@Composable
private fun InvitationCodeSection(
    code: String,
    inviteLink: String
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
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

            // 📋 코드 복사 버튼
            IconButton(
                onClick = {
                    val clip = ClipData.newPlainText("초대 코드", code)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "초대 코드가 복사되었습니다.", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "코드 복사")
            }

            // 🔗 링크 공유 버튼
            IconButton(onClick = {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, inviteLink)
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, "초대 링크 공유")
                context.startActivity(shareIntent)
            }) {
                Icon(Icons.Default.Share, contentDescription = "링크 공유")
            }
        }
    }
}

@Composable
private fun KakaoInviteButton(code: String) {
    val context = LocalContext.current

    Button(
        onClick = { shareRoomViaKakao(context, code) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "카카오톡으로 초대",
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("카카오톡으로 초대")
    }
}

fun shareRoomViaKakao(context: Context, code: String) {
    // 나중에 여기다가 딥링크나 앱 링크도 같이 넣으면 좋음
    val inviteMessage = """
        Gachiga에서 약속 방에 초대합니다! 🎉
        
        초대 코드: $code
        
        Gachiga 앱에서 '초대 코드로 참여하기'에 위 코드를 입력하면 방에 들어올 수 있어요.
    """.trimIndent()

    val kakaoPackage = "com.kakao.talk"

    // 1차: 카카오톡으로 바로 보내기
    val kakaoIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, inviteMessage)
        setPackage(kakaoPackage)
    }

    try {
        context.startActivity(kakaoIntent)
    } catch (e: ActivityNotFoundException) {
        // 카카오톡이 설치 안 되어 있으면 일반 공유로 fallback
        val genericIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, inviteMessage)
        }
        context.startActivity(
            Intent.createChooser(genericIntent, "공유할 앱을 선택하세요")
        )
        Toast.makeText(context, "카카오톡이 설치되어 있지 않아 일반 공유로 전환합니다.", Toast.LENGTH_SHORT).show()
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
    var carOptionMenuExpanded by remember { mutableStateOf(false) }
    var publicOptionMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelf) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) else CardDefaults.cardColors()
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
                            navController.navigate(
                                "${AppDestinations.MAP_SELECTION_SCREEN}/startPoint/-1?roomId=${roomId}"
                            )
                        }
                    },
                    enabled = isSelf // 본인만 활성화
                ) {
                    Text(member.startPoint)
                }
            }

            // 교통수단 설정
            InfoRow(icon = Icons.Default.DirectionsBus, title = "교통수단") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 대중교통 아이콘
                    TransportButton(
                        icon = Icons.Default.DirectionsTransit,
                        contentDescription = "대중교통",
                        isSelected = member.travelMode == TravelMode.TRANSIT,
                        onClick = {
                            if (isSelf) {
                                onStateChange(
                                    member.copy(
                                        travelMode = TravelMode.TRANSIT,
                                        isReady = false
                                    )
                                )
                            }
                        }
                    )

                    // 자동차 아이콘
                    TransportButton(
                        icon = Icons.Default.DirectionsCar,
                        contentDescription = "자동차",
                        isSelected = member.travelMode == TravelMode.CAR,
                        onClick = {
                            if (isSelf) {
                                onStateChange(
                                    member.copy(
                                        travelMode = TravelMode.CAR,
                                        isReady = false
                                    )
                                )
                            }
                        }
                    )

                    // 도보 아이콘
                    TransportButton(
                        icon = Icons.Default.DirectionsWalk,
                        contentDescription = "도보",
                        isSelected = member.travelMode == TravelMode.WALK,
                        onClick = {
                            if (isSelf) {
                                onStateChange(
                                    member.copy(
                                        travelMode = TravelMode.WALK,
                                        isReady = false
                                    )
                                )
                            }
                        }
                    )
                }
            }

            when (member.travelMode) {
                TravelMode.CAR -> {
                    InfoRow(icon = Icons.Default.Tune, title = "경로 옵션") {
                        Box {
                            TextButton(
                                onClick = { if (isSelf) carOptionMenuExpanded = true },
                                enabled = isSelf
                            ) {
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
                                            if (isSelf) {
                                                val code = when (option) {
                                                    CarRouteOption.RECOMMEND -> 0
                                                    CarRouteOption.FREE -> 1
                                                    CarRouteOption.FASTEST -> 2
                                                    CarRouteOption.SHORTEST -> 10
                                                }
                                                onStateChange(
                                                    member.copy(
                                                        carOption = option,
                                                        searchOption = code,
                                                        isReady = false
                                                    )
                                                )
                                            }
                                            carOptionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                TravelMode.TRANSIT -> {
                    InfoRow(icon = Icons.Default.Tune, title = "경로 옵션") {
                        Box {
                            TextButton(
                                onClick = { if (isSelf) publicOptionMenuExpanded = true },
                                enabled = isSelf
                            ) {
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
                                            if (isSelf) {
                                                val code = when (option) {
                                                    PublicTransitOption.OPTIMAL -> 0
                                                    PublicTransitOption.LEAST_TRANSFER -> 1
                                                    PublicTransitOption.FASTEST -> 2
                                                    PublicTransitOption.LEAST_WALKING -> 3
                                                }
                                                onStateChange(
                                                    member.copy(
                                                        publicTransitOption = option,
                                                        searchOption = code,
                                                        isReady = false
                                                    )
                                                )
                                            }
                                            publicOptionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                TravelMode.WALK -> {
                    // 도보는 옵션 없음
                }
            }

            // 준비 완료 버튼 (본인만 보임)
            if (isSelf && !member.isReady) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onStateChange(member.copy(isReady = true)) },
                    enabled = member.startPoint != "미설정" && member.startPoint != "위치 선택 전",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("준비 완료")
                }
            }
        }
    }
}