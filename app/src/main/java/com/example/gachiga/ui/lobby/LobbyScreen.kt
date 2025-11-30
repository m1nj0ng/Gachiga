package com.example.gachiga.ui.lobby

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gachiga.data.LoggedInState
import com.example.gachiga.data.RoomDetail
import com.example.gachiga.navigation.AppDestinations
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    navController: NavController,
    state: LoggedInState,
    onRoomCreated: (RoomDetail) -> Unit,
    onJoinRoom: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var inputCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gachiga 로비") },
                actions = {
                    // 프로필 아이콘을 클릭하면 메뉴가 토글
                    Box {
                        IconButton(onClick = { menuExpanded = !menuExpanded }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "내 정보"
                            )
                        }

                        // 로그아웃 드롭다운 메뉴
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            // "로그아웃" 메뉴 아이템
                            DropdownMenuItem(
                                text = { Text("로그아웃") },
                                onClick = {
                                    menuExpanded = false // 메뉴를 닫음
                                    // ★★★ 4. 로그아웃 로직 실행 ★★★
                                    coroutineScope.launch {
                                        UserApiClient.instance.logout { error ->
                                            if (error != null) {
                                                Log.e("KAKAO_LOGOUT", "로그아웃 실패. SDK에서 토큰 삭제됨", error)
                                            } else {
                                                Log.i("KAKAO_LOGOUT", "로그아웃 성공. SDK에서 토큰 삭제됨")
                                            }
                                            // 로그아웃 성공/실패 여부와 관계없이 시작 화면으로 이동
                                            navController.navigate(AppDestinations.START_SCREEN) {
                                                // 백스택의 모든 화면을 제거하여 뒤로가기 시 로비로 돌아오지 않도록 함
                                                popUpTo(navController.graph.id) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Logout,
                                        contentDescription = "로그아웃 아이콘"
                                    )
                                }
                            )
                            // TODO: 여기에 다른 메뉴(예: 회원탈퇴)를 추가할 수 있습니다.
                        }
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
                onClick = { onRoomCreated(RoomDetail()) },
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
                value = inputCode, // 1. 아까 만든 변수와 연결
                onValueChange = { inputCode = it }, // 2. 타자 칠 때마다 변수 업데이트
                label = { Text("초대 코드 입력") },
                placeholder = { Text("6자리 코드 입력") },
                singleLine = true, // 한 줄만 입력 가능하게
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            // 3. 코드가 비어있지 않으면 '참여하기' 실행!
                            if (inputCode.isNotBlank()) {
                                onJoinRoom(inputCode)
                            }
                        }
                    ) {
                        Text("참여", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}