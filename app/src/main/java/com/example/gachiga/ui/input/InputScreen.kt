package com.example.gachiga.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gachiga.navigation.AppDestinations
import com.example.gachiga.data.GachigaState
import com.example.gachiga.data.Member
import com.example.gachiga.ui.input.InfoRow
import com.example.gachiga.ui.input.TimePickerDialog
import com.example.gachiga.ui.input.TransportButton

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

// --- 공통 정보 섹션 (목적지, 도착시간) ---
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
            Button(onClick = {
                navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/destination/-1")
            }) {
                Text(state.destination)
            }
        }
        InfoRow(icon = Icons.Default.Schedule, title = "도착 시간") {
            Button(onClick = { showTimePicker = true }) {
                Text(state.arrivalTime)
            }
        }
    }

    if (showTimePicker) {
        // 1. TimePickerDialog에 전달할 초기 시간을 파싱합니다.
        val initialHour = state.arrivalTime.substringBefore(":").toIntOrNull() ?: 12
        val initialMinute = state.arrivalTime.substringAfter(":").toIntOrNull() ?: 0

        // 2. CommonUI.kt에 정의된 파라미터 형식에 맞게 호출합니다.
        TimePickerDialog(
            initialHour = initialHour,
            initialMinute = initialMinute,
            onTimeSelected = { hour, minute ->
                val formattedTime = String.format("%02d:%02d", hour, minute)
                onStateChange(state.copy(arrivalTime = formattedTime))
                // onTimeSelected 안에서는 showTimePicker를 false로 바꿀 필요가 없습니다.
                // onDismiss가 항상 호출되기 때문입니다.
            },
            onDismiss = {
                // '확인' 또는 '취소'를 누르거나, 바깥을 클릭하면 항상 호출됩니다.
                showTimePicker = false
            }
        )
    }
}

// --- 멤버별 정보 섹션 ---
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
                val newMember = Member(name = "멤버 ${members.size + 1}")
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

// --- 각 멤버의 정보 카드 ---
@Composable
fun MemberCard(
    navController: NavController,
    member: Member,
    memberIndex: Int,
    onMemberChange: (Member) -> Unit,
    onRemoveMember: () -> Unit
) {
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
                IconButton(onClick = onRemoveMember) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "멤버 삭제")
                }
            }
            InfoRow(icon = Icons.Default.MyLocation, title = "출발지") {
                Button(onClick = {
                    navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/startPoint/$memberIndex")
                }) {
                    Text(member.startPoint)
                }
            }
            InfoRow(icon = Icons.Default.DirectionsCar, title = "교통수단") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // "대중교통" 버튼
                    TransportButton(
                        text = "대중교통",
                        // usePublicTransit 값에 따라 선택 상태 결정
                        isSelected = member.usePublicTransit,
                        onClick = {
                            // usePublicTransit 값을 반전(toggle)시켜서 상태 변경
                            onMemberChange(member.copy(usePublicTransit = !member.usePublicTransit))
                        }
                    )
                    // "자차" 버튼
                    TransportButton(
                        text = "자차",
                        // useCar 값에 따라 선택 상태 결정
                        isSelected = member.useCar,
                        onClick = {
                            // useCar 값을 반전(toggle)시켜서 상태 변경
                            onMemberChange(member.copy(useCar = !member.useCar))
                        }
                    )
                }
            }
        }
    }
}