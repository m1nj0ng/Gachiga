package com.example.gachiga.ui.start

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 로그인 / 비로그인 진행을 선택하는 앱의 첫 화면
 * @param onNavigateToLogin 로그인 버튼 클릭 시 호출될 콜백
 * @param onNavigateToInput 로그인 없이 진행 버튼 클릭 시 호출될 콜백
 */
@Composable
fun StartScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToInput: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Gachiga",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "모두의 중간지점 찾기",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(150.dp))

            // --- 로그인 버튼 ---
            Button(
                onClick = onNavigateToLogin, // 클릭 시 로그인 화면으로 이동하는 콜백 호출
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("로그인하고 시작하기", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 로그인 없이 진행 버튼 ---
            OutlinedButton(
                onClick = onNavigateToInput, // 클릭 시 비로그인 입력 화면으로 이동
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("로그인 없이 진행하기", fontSize = 16.sp)
            }
        }
    }
}