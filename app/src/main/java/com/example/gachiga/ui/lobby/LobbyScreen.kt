package com.example.gachiga.ui.lobby

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gachiga.data.LoggedInState
import com.example.gachiga.data.RoomDetail
import com.example.gachiga.navigation.AppDestinations
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    navController: NavController,
    state: LoggedInState,
    onRoomCreated: (RoomDetail) -> Unit,
    onJoinRoom: (String, () -> Unit, () -> Unit) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var invitationCodeInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 뒤로가기 눌렀을 때 띄울 다이얼로그 상태
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 시스템 뒤로가기 가로채기
    BackHandler(enabled = true) {
        showLogoutConfirm = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gachiga 로비") },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = !menuExpanded }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "내 정보"
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("로그아웃") },
                                onClick = {
                                    menuExpanded = false
                                    coroutineScope.launch {
                                        UserApiClient.instance.logout { error ->
                                            if (error != null) {
                                                Log.e("KAKAO_LOGOUT", "로그아웃 실패. SDK에서 토큰 삭제됨", error)
                                            } else {
                                                Log.i("KAKAO_LOGOUT", "로그아웃 성공. SDK에서 토큰 삭제됨")
                                            }
                                            navController.navigate(AppDestinations.START_SCREEN) {
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
                value = invitationCodeInput,
                onValueChange = { newValue ->
                    invitationCodeInput = newValue.uppercase()
                },
                label = { Text("초대 코드 입력") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (invitationCodeInput.isNotBlank()) {
                                onJoinRoom(
                                    invitationCodeInput,
                                    {
                                        // 성공 시 (원하면 토스트나 로그 넣어도 됨)
                                        // 예: Toast.makeText(context, "참여 완료!", Toast.LENGTH_SHORT).show()
                                    },
                                    {
                                        Toast.makeText(
                                            context,
                                            "초대 코드를 다시 확인해주세요.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    ) {
                        Text("참여")
                    }
                }
            )
        }
    }
    // 🔹 뒤로가기 시 로그아웃 확인 다이얼로그
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("로그아웃") },
            text = { Text("로그아웃 하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        coroutineScope.launch {
                            // Kakao 로그아웃
                            UserApiClient.instance.logout { error ->
                                if (error != null) {
                                    Log.e("KAKAO_LOGOUT", "로그아웃 실패. SDK에서 토큰 삭제됨", error)
                                } else {
                                    Log.i("KAKAO_LOGOUT", "로그아웃 성공. SDK에서 토큰 삭제됨")
                                }
                                // Firebase 로그아웃
                                Firebase.auth.signOut()

                                // 시작 화면으로 이동 + 백스택 모두 제거
                                navController.navigate(AppDestinations.START_SCREEN) {
                                    popUpTo(navController.graph.id) {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}