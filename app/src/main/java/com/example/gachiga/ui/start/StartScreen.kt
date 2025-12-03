package com.example.gachiga.ui.start

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.auth.model.OAuthToken
import kotlinx.coroutines.launch

/**
 * 로그인 / 비로그인 진행을 선택하는 앱의 첫 화면
 * @param onNavigateToLogin 로그인 버튼 클릭 시 호출될 콜백
 * @param onNavigateToInput 로그인 없이 진행 버튼 클릭 시 호출될 콜백
 */
@Composable
fun StartScreen(
    onNavigateToLogin: (String) -> Unit,
    onNavigateToInput: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

            // 로그인 버튼
            Button(
                onClick = {
                    coroutineScope.launch {
                        // 로그인 성공/실패 시 공통으로 처리할 콜백 함수
                        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
                            if (error != null) {
                                Log.e("KAKAO_LOGIN", "로그인 실패", error)
                            } else if (token != null) {
                                Log.i("KAKAO_LOGIN", "로그인 성공 ${token.accessToken}")

                                onNavigateToLogin(token.accessToken)
                            }
                        }

                        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                                if (error != null) {
                                    Log.e("KAKAO_LOGIN", "카카오톡 로그인 실패", error)
                                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                                        return@loginWithKakaoTalk
                                    }
                                    UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                                } else if (token != null) {
                                    onNavigateToLogin(token.accessToken)
                                }
                            }
                        } else {
                            UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "카카오톡으로 로그인하기",
                    color = Color.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 로그인 없이 진행 버튼
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