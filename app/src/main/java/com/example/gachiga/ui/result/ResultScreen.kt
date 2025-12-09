package com.example.gachiga.ui.result

import android.content.Intent
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.gachiga.data.CalculationResult
import com.example.gachiga.data.GachigaState
import com.example.gachiga.data.RouteRepository
import com.example.gachiga.data.TravelMode
import com.example.gachiga.util.RouteLogicManager
import com.example.gachiga.util.RouteVisualizer
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavController,
    repository: RouteRepository, // 더미 데이터 대신 Repository 받음
    gachigaState: GachigaState,   // 사용자 입력 정보 받음
    onBackToEdit: () -> Unit, // 추가: Navigation에서 넘겨준 뒤로 가기 함수 받음
    currentUserId: Int? = null // ★ [추가] 내 ID를 알아야 내 경로를 찾습니다.
) {
    // ★ 공유용 Context
    val context = LocalContext.current

    // ★ 결과 공유 함수
    fun shareResult(text: String) {
        if (text.isBlank()) return

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Gachiga 중간지점 결과")
            putExtra(Intent.EXTRA_TEXT, text)
        }

        val shareIntent = Intent.createChooser(sendIntent, "결과를 공유할 앱을 선택하세요")
        context.startActivity(shareIntent)
    }

    // 1. 로직 매니저 생성
    val logicManager = remember { RouteLogicManager(repository) }

    // 2. 결과 로그를 담을 상태 변수
    // ★ [수정] String 하나가 아니라 결과 객체와 화면 표시용 텍스트를 분리
    var calcResult by remember { mutableStateOf<CalculationResult?>(null) }
    var calculationLog by remember { mutableStateOf("지도를 불러오는 중입니다...") }

    // ★ [추가] 로딩 상태와 모드 상태
    var isCalculating by remember { mutableStateOf(true) }
    var isMyRouteMode by remember { mutableStateOf(false) }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var visualizer by remember { mutableStateOf<RouteVisualizer?>(null) }

    // 3. 비동기 실행을 위한 Scope
    val coroutineScope = rememberCoroutineScope()

    // ★ [수정] 뒤로가기 핸들링: 모드에 따라 동작 분기
    BackHandler {
        if (isMyRouteMode) {
            isMyRouteMode = false
        } else {
            onBackToEdit()
        }
    }

    // ★ [추가] 모드 변경 감지 -> 지도 다시 그리기 (API 호출 없이!)
    LaunchedEffect(isMyRouteMode) {
        val res = calcResult ?: return@LaunchedEffect
        val viz = visualizer ?: return@LaunchedEffect

        if (isMyRouteMode) {
            // [A] 내 경로 모드
            if (res.myPathPoints != null && res.myLog != null) {
                calculationLog = res.myLog!!
                val myColor = gachigaState.members.find { it.id == currentUserId }?.color ?: 0xFF1976D2.toInt()

                // ★ [수정] 파란 경로와 빨간 경로를 함께 전달하여 그림
                viz.drawFocusedRoute(res.myPathPoints, res.myRedPathPoints, myColor)
            } else {
                isMyRouteMode = false // 데이터 없으면 강제 복귀
            }
        } else {
            // [B] 전체 모드 복구
            calculationLog = res.fullLog

            // 1. 지도 깨끗이 지우기
            viz.clear()

            // 2. 파란색/초록색 멤버 경로 복구
            res.allRoutes.forEach { (memberId, segment) ->
                val member = gachigaState.members.find { it.id == memberId } ?: return@forEach
                val rawPaths = res.rawTransitPaths[memberId]

                // ★ [추가] 자르는 위치 가져오기 (없으면 끝까지)
                val cutIdx = res.memberCutIndices[memberId] ?: Int.MAX_VALUE

                if (member.mode == TravelMode.TRANSIT) {
                    // ★ [수정] 잘라야 하는 위치(cutIdx)를 전달
                    viz.drawTransitRouteCut(rawPaths ?: emptyList(), cutIdx, member.color)
                } else {
                    // ★ [수정] 자동차/도보도 cutIdx 지점까지만 잘라서 그리기 (유령 경로 방지)
                    val pointsToDraw = if (cutIdx != Int.MAX_VALUE && cutIdx < segment.points.size) {
                        segment.points.take(cutIdx + 1)
                    } else {
                        segment.points
                    }
                    viz.drawPolyline(pointsToDraw, member.color)
                }
            }

            // 3. 빨간색 합류선(Red Lines) 복구
            res.redLines.forEach { (points, isTransitLeader) ->
                viz.drawRedLine(points, isTransitLeader)
            }

            // 4. 카메라 전체 앵글로 복구
            viz.moveCameraToFit(res.allPointsForCamera)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                // ★ [수정] 타이틀 동적 변경
                Text(if (isMyRouteMode) "나의 상세 경로" else "중간지점 계산 결과", fontWeight = FontWeight.Bold)
            }, navigationIcon = {
                IconButton(onClick = {
                    // ★ [수정] 뒤로가기 버튼 동작도 BackHandler와 동일하게
                    if (isMyRouteMode) isMyRouteMode = false else onBackToEdit()
                }) {
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
                                    // ★ [수정] 로컬 변수(newVisualizer)에 먼저 담고 -> 상태(visualizer)에 저장합니다.
                                    val newVisualizer = RouteVisualizer(kakaoMap)
                                    visualizer = newVisualizer // 바깥 상태 변수 업데이트 (UI 갱신용)

                                    // 2. 입력 데이터 준비 (목적지 좌표 등)
                                    val destX = gachigaState.destX
                                    val destY = gachigaState.destY

                                    if (destX != null && destY != null && calcResult == null) {
                                        calculationLog = "경로 계산 중..."

                                        // 3. 코루틴으로 계산 시작
                                        coroutineScope.launch {
                                            try {
                                                // 도착 시간 파싱 (HH:mm 문자열 -> Calendar)
                                                val targetTime = parseTime(gachigaState.arrivalTime)

                                                // 4. 진짜 계산 로직 호출! (지도 그리기 + 로그 생성)
                                                val result = logicManager.calculateRoutes(
                                                    members = gachigaState.members,
                                                    destName = gachigaState.destination, // ★ [추가] 목적지 이름 전달
                                                    destX = destX,
                                                    destY = destY,
                                                    targetTime = targetTime,
                                                    // ★ [수정] 상태 변수(visualizer) 대신
                                                    // 로컬 변수(newVisualizer)를 넘깁니다.
                                                    visualizer = newVisualizer,
                                                    myMemberId = currentUserId
                                                )
                                                // 5. 결과 업데이트
                                                calcResult = result // ★ [수정] 객체 저장
                                                calculationLog = result.fullLog // ★ [수정] 전체 로그 표시
                                                isCalculating = false // ★ [추가]

                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                calculationLog = "계산 중 오류 발생: ${e.message}"
                                                isCalculating = false // ★ [추가]
                                            }
                                        }
                                    } else if (destX == null || destY == null) {
                                        calculationLog = "오류: 목적지 좌표가 설정되지 않았습니다."
                                        isCalculating = false
                                    }
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // ★ [추가] 로딩 인디케이터
                if (isCalculating) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            // [하단] 결과 로그 영역 (화면의 40% 차지, 스크롤 가능)
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // ★ [수정] 텍스트 영역을 Box로 감싸고 weight를 줘서 버튼 공간 확보
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
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

                Spacer(modifier = Modifier.height(8.dp))

                // 비로그인(= currentUserId == null)일 때만 결과 공유 버튼 표시
                if (currentUserId == null) {
                    Button(
                        onClick = { shareResult(calculationLog) },
                        enabled = !isCalculating && calculationLog.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "결과 공유"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "결과 공유하기",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ★ [추가] 하단 토글 버튼 (내 경로 데이터가 있을 때만 표시)
                if (!calcResult?.myLog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { isMyRouteMode = !isMyRouteMode },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (isMyRouteMode) "전체 경로 다시 보기" else "내 경로만 자세히 보기",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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