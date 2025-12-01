package com.example.gachiga.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.gachiga.data.GachigaState
import com.example.gachiga.data.RouteRepository
import com.example.gachiga.util.RouteLogicManager
import com.example.gachiga.util.RouteVisualizer
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavController,
    repository: RouteRepository, // ★ [변경] 더미 데이터 대신 Repository 받음
    gachigaState: GachigaState   // ★ [변경] 사용자 입력 정보 받음
) {
    // 1. 로직 매니저 생성
    val logicManager = remember { RouteLogicManager(repository) }

    // 2. 결과 로그를 담을 상태 변수
    var calculationLog by remember { mutableStateOf("지도를 불러오는 중입니다...") }

    // 3. 비동기 실행을 위한 Scope
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("중간지점 계산 결과", fontWeight = FontWeight.Bold)
            }, navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로가기")
                }
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // [상단] 지도 영역 (화면의 60% 차지)
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth()
                    .background(Color.LightGray)
            ) {
                AndroidView(
                    factory = { context ->
                        MapView(context).apply {
                            this.start(object : MapLifeCycleCallback() {
                                override fun onMapDestroy() {}
                                override fun onMapError(error: Exception) {
                                    calculationLog = "지도 에러: ${error.message}"
                                }
                            }, object : KakaoMapReadyCallback() {
                                override fun onMapReady(kakaoMap: KakaoMap) {
                                    // ★★★ [핵심] 지도가 준비되면 로직 실행 ★★★

                                    // 1. 화가(Visualizer) 생성
                                    val visualizer = RouteVisualizer(kakaoMap)

                                    // 2. 입력 데이터 준비 (목적지 좌표 등)
                                    val destX = gachigaState.destX
                                    val destY = gachigaState.destY

                                    if (destX != null && destY != null) {
                                        calculationLog = "경로 계산 중..."

                                        // 3. 코루틴으로 계산 시작
                                        coroutineScope.launch {
                                            try {
                                                // 도착 시간 파싱 (HH:mm 문자열 -> Calendar)
                                                val targetTime = parseTime(gachigaState.arrivalTime)

                                                // 4. 진짜 계산 로직 호출! (지도 그리기 + 로그 생성)
                                                val result = logicManager.calculateRoutes(
                                                    members = gachigaState.members,
                                                    destX = destX,
                                                    destY = destY,
                                                    targetTime = targetTime,
                                                    visualizer = visualizer
                                                )
                                                // 5. 결과 텍스트 업데이트
                                                calculationLog = result

                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                calculationLog = "계산 중 오류 발생: ${e.message}"
                                            }
                                        }
                                    } else {
                                        calculationLog = "오류: 목적지 좌표가 설정되지 않았습니다."
                                    }
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // [하단] 결과 로그 영역 (화면의 40% 차지, 스크롤 가능)
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()) // 스크롤 가능하게
            ) {
                Text(
                    text = "📄 상세 경로 정보",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = calculationLog,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// [Helper] "14:00" 같은 문자열을 Calendar 객체로 변환
private fun parseTime(timeStr: String): Calendar? {
    return try {
        val parts = timeStr.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            // 만약 현재 시간보다 이전이면 내일로 설정 (선택 사항)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    } catch (e: Exception) {
        null
    }
}