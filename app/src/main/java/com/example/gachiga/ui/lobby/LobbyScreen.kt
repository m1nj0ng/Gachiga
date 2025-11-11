package com.example.gachiga.ui.lobby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gachiga.data.LoggedInState
import com.example.gachiga.navigation.AppDestinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(navController: NavController, state: LoggedInState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gachiga 로비") },
                actions = {
                    // 프로필 아이콘 (임시)
                    IconButton(onClick = { /* TODO: 프로필 화면으로 이동 */ }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "내 정보")
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TODO: 참여 중인 약속 목록 표시 (state.joinedRooms)
            Text(
                "${state.currentUser?.nickname ?: "사용자"}님, 환영합니다!",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // 새 약속 만들기 버튼
            Button(
                onClick = { /* TODO: 방 만들기 화면으로 이동하는 로직 추가 */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("새 약속 만들기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 초대 코드로 참여하기
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("초대 코드 입력") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { /* TODO: 코드로 참여 로직 */ }) {
                        Text("참여")
                    }
                })
        }
    }
}