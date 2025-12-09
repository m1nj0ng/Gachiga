package com.example.gachiga.navigation

import android.util.Log
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.gachiga.data.*
import com.example.gachiga.ui.input.InputScreen
import com.example.gachiga.ui.lobby.LobbyScreen
import com.example.gachiga.ui.map.MapSelectionScreen
import com.example.gachiga.ui.result.ResultScreen
import com.example.gachiga.ui.result.VoteScreen
import com.example.gachiga.ui.room.RoomDetailScreen
import com.example.gachiga.ui.start.StartScreen
import com.kakao.sdk.user.UserApiClient
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import androidx.navigation.NavType
import android.widget.Toast
import androidx.navigation.navArgument
import com.example.gachiga.ui.input.MidpointSelectScreen
import com.example.gachiga.util.RouteLogicManager
import com.kakao.vectormap.LatLng
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

object AppDestinations {
    const val START_SCREEN = "start"
    const val LOBBY_SCREEN = "lobby"
    const val INPUT_SCREEN = "input"
    const val MAP_SELECTION_SCREEN = "map_selection"
    const val ROOM_DETAIL_SCREEN = "room_detail/{roomId}"
    const val RESULT_SCREEN = "result"
    const val MIDPOINT_SELECT_SCREEN = "midpoint_select"
}

val USER_COLORS = listOf(
    0xFF1976D2.toInt(), // 파랑
    0xFFFF9800.toInt(), // 주황
    0xFF388E3C.toInt(), // 초록
    0xFF7B1FA2.toInt(), // 보라
    0xFF0097A7.toInt(), // 청록
    0xFFC2185B.toInt(), // 자주
    0xFF5D4037.toInt(), // 갈색
    0xFFFFC107.toInt()  // 노랑
)

@Composable
fun GachigaApp(
    navController: NavHostController,
    repository: RouteRepository, // ★ [추가] Repository 주입 받음
    nonLoggedInState: GachigaState,
    loggedInState: LoggedInState,
    onNonLoggedInStateChange: (GachigaState) -> Unit,
    onLoggedInStateChange: (LoggedInState) -> Unit
) {
    var roomDetailState by remember { mutableStateOf<RoomDetail?>(null) }

    NavHost(navController = navController, startDestination = AppDestinations.START_SCREEN) {

        composable(AppDestinations.START_SCREEN) {
            StartScreen(
                onNavigateToLogin = { kakaoAccessToken ->
                    getFirebaseCustomToken(kakaoAccessToken) { firebaseCustomToken ->

                        signInToFirebaseWithCustomToken(firebaseCustomToken) { firebaseUser ->

                            // 2. Firebase 로그인 성공 -> 사용자 정보 가져오기
                            UserApiClient.instance.me { user, error ->
                                if (user != null) {
                                    onLoggedInStateChange(
                                        loggedInState.copy(
                                            currentUser = User(
                                                id = firebaseUser.uid, // [중요] Firebase UID 저장
                                                nickname = user.kakaoAccount?.profile?.nickname ?: "사용자",
                                                profileImageUrl = user.kakaoAccount?.profile?.thumbnailImageUrl ?: ""
                                            )
                                        )
                                    )
                                    // 로비로 이동
                                    navController.navigate(AppDestinations.LOBBY_SCREEN) {
                                        popUpTo(AppDestinations.START_SCREEN) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                },
                onNavigateToInput = {
                    navController.navigate(AppDestinations.INPUT_SCREEN)
                }
            )
        }

        composable(AppDestinations.LOBBY_SCREEN) { // 수정: 방 만들기 버튼 눌렀을 때
            LobbyScreen(
                navController = navController,
                state = loggedInState,

                onRoomCreated = { newRoom ->
                    val currentUser = loggedInState.currentUser

                    if (currentUser != null) {
                        createRoomInFirestore(
                            hostUser = currentUser,
                            onSuccess = { createdRoomId, inviteLink ->
                                if (createdRoomId.isNotBlank()) {
                                    Log.d("Navigation", "방 생성 성공! ID: $createdRoomId")

                                    val hostMember = RoomMember(user = currentUser, isHost = true)
                                    roomDetailState = newRoom.copy(
                                        roomId = createdRoomId,
                                        invitationCode = createdRoomId,
                                        inviteLink = inviteLink,
                                        members = listOf(hostMember)
                                    )

                                    navController.navigate("room_detail/$createdRoomId")
                                }
                            },
                            onFailure = { e ->
                                Log.e("Lobby", "방 만들기 실패", e)
                            }
                        )
                    }
                },
                onJoinRoom = { inputCode, onSuccess, onFailure -> // 추가: 참여하기 함수 호출
                    val currentUser = loggedInState.currentUser
                    if (currentUser != null) {
                        joinRoomInFirestore(
                            roomId = inputCode,
                            joinUser = currentUser,
                            onSuccess = {
                                onSuccess()
                                navController.navigate("room_detail/$inputCode")
                            },
                            onFailure = {
                                onFailure()
                                Log.e("Lobby", "참여 실패: ${it.message}")
                            }
                        )
                    } else {
                        onFailure()
                    }
                }
            )
        }

        composable(AppDestinations.INPUT_SCREEN) {
            InputScreen(
                navController = navController,
                gachigaState = nonLoggedInState,
                onStateChange = onNonLoggedInStateChange
            )
        }

        // ★ [추가] 추천 장소 선택 화면 연결
        composable(AppDestinations.MIDPOINT_SELECT_SCREEN) {
            MidpointSelectScreen(
                navController = navController,
                repository = repository,
                members = nonLoggedInState.members,
                onPlaceSelected = { place ->
                    // 1. 선택된 장소 정보로 상태 업데이트
                    onNonLoggedInStateChange(
                        nonLoggedInState.copy(
                            destination = place.placeName,
                            destX = place.longitude,
                            destY = place.latitude
                        )
                    )
                    // 2. 입력 화면으로 복귀
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = AppDestinations.ROOM_DETAIL_SCREEN,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            val currentUser = loggedInState.currentUser

            // ★ 로직 매니저와 스코프 준비 (추천 기능 사용을 위해)
            val logicManager = remember { RouteLogicManager(repository) }
            val scope = rememberCoroutineScope()

            if (roomId != null && currentUser != null) {

                // 실시간 감시 + 감지 로직
                LaunchedEffect(roomId) {
                    val db = Firebase.firestore
                    db.collection("rooms").document(roomId)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) return@addSnapshotListener

                            if (snapshot != null && snapshot.exists()) {
                                val roomData = snapshot.toObject(RoomDetail::class.java)
                                if (roomData != null) {
                                    roomDetailState = roomData // 1. 화면 갱신

                                    // 2. isCalculating 켜졌는지 확인!
                                    if (roomData.isCalculating) {

                                        // 데이터를 GachigaState으로 변환 (로직용 Member로 변환)
                                        // ★ [핵심 수정] mapIndexed로 순서에 맞는 색상 부여
                                        val convertedMembers = roomData.members.mapIndexed { index, roomMember ->
                                            // 순서대로 색상 꺼내오기 (Int 값 그대로 사용)
                                            val assignedColor = USER_COLORS[index % USER_COLORS.size]

                                            com.example.gachiga.data.Member(
                                                id = roomMember.user.id.hashCode(),
                                                name = roomMember.user.nickname,
                                                startPoint = roomMember.startPoint,
                                                x = roomMember.x,
                                                y = roomMember.y,
                                                placeName = roomMember.startPoint,
                                                mode = roomMember.travelMode,

                                                // ★ [수정] 할당된 색상 적용
                                                color = assignedColor,

                                                carOption = roomMember.carOption,
                                                publicTransitOption = roomMember.publicTransitOption,
                                                searchOption = roomMember.searchOption
                                            )
                                        }

                                        val tempState = nonLoggedInState.copy(
                                            destination = roomData.destination,
                                            destX = roomData.destX,
                                            destY = roomData.destY,
                                            arrivalTime = roomData.arrivalTime,
                                            members = convertedMembers
                                        )

                                        // (2) 데이터 세팅
                                        onNonLoggedInStateChange(tempState)

                                        if (navController.currentDestination?.route != AppDestinations.RESULT_SCREEN) {
                                            navController.navigate(AppDestinations.RESULT_SCREEN)
                                        }
                                    } else {
                                        if (navController.currentDestination?.route == AppDestinations.RESULT_SCREEN) {
                                            navController.popBackStack()
                                        }
                                    }
                                }
                            }
                        }
                }

                // 뒤로 가기/방 나가기 로직
                val handleBackAction = {
                    if (roomDetailState != null) {
                        val members = roomDetailState!!.members
                        val isHost = members.find { it.user.id == currentUser.id }?.isHost ?: false
                        val context = navController.context

                        if (isHost) {
                            // --- Case 1: 방장 (Host) 나갈 때 ---
                            if (members.size > 1) {
                                // 1-1. 남은 멤버가 있으면 방장 위임 및 퇴장
                                transferHostAndLeaveRoomInFirestore(
                                    roomId = roomId,
                                    oldHostUser = currentUser,
                                    onSuccess = {
                                        Toast.makeText(context, "방장이 위임되었습니다.", Toast.LENGTH_SHORT).show()
                                        navController.navigate(AppDestinations.LOBBY_SCREEN) {
                                            popUpTo(AppDestinations.LOBBY_SCREEN) { inclusive = true }
                                        }
                                    },
                                    onFailure = { e ->
                                        Toast.makeText(context, "방장 위임 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                        navController.navigate(AppDestinations.LOBBY_SCREEN)
                                    }
                                )
                            } else {
                                // 1-2. 혼자면 방 삭제
                                deleteRoomInFirestore(roomId, onSuccess = {
                                    Toast.makeText(context, "방이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                    navController.navigate(AppDestinations.LOBBY_SCREEN) {
                                        popUpTo(AppDestinations.LOBBY_SCREEN) { inclusive = true }
                                    }
                                }, onFailure = { e ->
                                    Toast.makeText(context, "방 삭제 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                    navController.navigate(AppDestinations.LOBBY_SCREEN)
                                })
                            }

                        } else {
                            // --- Case 2: 멤버 (Member) 나갈 때 ---
                            leaveRoomInFirestore(
                                roomId = roomId,
                                leaveUser = currentUser,
                                onSuccess = {
                                    Toast.makeText(context, "방에서 나왔습니다.", Toast.LENGTH_SHORT).show()
                                    navController.navigate(AppDestinations.LOBBY_SCREEN) {
                                        popUpTo(AppDestinations.LOBBY_SCREEN) { inclusive = true }
                                    }
                                },
                                onFailure = { e ->
                                    Toast.makeText(context, "방 나가기 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                    navController.navigate(AppDestinations.LOBBY_SCREEN)
                                }
                            )
                        }
                    }
                }

                if (roomDetailState != null) {
                    val nonNullRoomId = roomId!!
                    // 추천 경로가 비어있으면 -> 방 상세 화면 (대기방)
                    if (roomDetailState!!.suggestedRoutes.isEmpty()) {
                        RoomDetailScreen(
                            navController = navController,
                            loggedInUser = currentUser,
                            roomDetail = roomDetailState!!,

                            // 방 정보 저장 (기존 코드 수정)
                            onStateChange = { updatedRoom ->
                                val isHost = roomDetailState!!.members.find {
                                    it.user.id == currentUser.id
                                }?.isHost == true

                                if (isHost) {
                                    val updates = mutableMapOf<String, Any>(
                                        "destination" to updatedRoom.destination,
                                        "arrivalTime" to updatedRoom.arrivalTime
                                    )

                                    // 1. 목적지 좌표 업데이트
                                    if (updatedRoom.destY != 0.0 && updatedRoom.destX != 0.0) {
                                        updates["destY"] = updatedRoom.destY
                                        updates["destX"] = updatedRoom.destX
                                    } else {
                                        // X 버튼 눌러서 초기화된 경우 (좌표 0.0)
                                        updates["destY"] = 0.0
                                        updates["destX"] = 0.0
                                    }

                                    // ★ [추가] 목적지가 "미설정"으로 초기화되면 -> 모든 멤버의 투표 상태 리셋
                                    if (updatedRoom.destination == "미설정") {
                                        // 1. 추천 경로 리스트 비우기
                                        updates["suggestedRoutes"] = emptyList<SuggestedRoute>()

                                        // 2. 모든 멤버의 voted = false 로 초기화
                                        roomDetailState!!.members.forEach { member ->
                                            if (member.voted) { // 투표한 사람만 굳이 찾아서
                                                val resetMember = member.copy(voted = false)
                                                updateMemberInFirestore(roomId, resetMember) {}
                                            }
                                        }
                                    }

                                    updateRoomInFirestore(roomId, updates) {}
                                    roomDetailState = updatedRoom
                                }
                            },

                            // 멤버 정보 저장
                            onMemberUpdate = { updatedMember ->
                                updateMemberInFirestore(roomId, updatedMember) {}
                            },

                            // [기존] 계산 버튼 클릭 시
                            onCalculate = {
                                val isHost = roomDetailState!!.members.find {
                                    it.user.id == currentUser.id
                                }?.isHost == true

                                if (isHost) {
                                    val updates = mapOf("isCalculating" to true)
                                    updateRoomInFirestore(roomId, updates) {
                                        Log.e("Navigation", "계산 신호 전송 실패")
                                    }
                                }
                            },

                            // 뒤로 가기
                            onBackAction = handleBackAction,

                            // ★ [추가] 추천받기 버튼 클릭 시 (목적지 미설정일 때 호출됨)
                            onRecommend = {
                                val isHost = roomDetailState!!.members.find { it.user.id == currentUser.id }?.isHost == true
                                if (isHost) {
                                    // 1. 코루틴 실행
                                    scope.launch {
                                        // 2. 현재 방 멤버들을 로직용 Member 객체로 변환 (좌표만 있으면 됨)
                                        val membersForLogic = roomDetailState!!.members.map {
                                            com.example.gachiga.data.Member(
                                                id = it.user.id.hashCode(),
                                                name = it.user.nickname,
                                                x = it.x,
                                                y = it.y
                                            )
                                        }

                                        // 3. 로직 매니저 호출 (무게중심 -> 카테고리별 추천 장소 리스트 반환)
                                        val recommendations = logicManager.recommendMidpointPlaces(membersForLogic)

                                        // 4. Firestore에 저장 -> SnapshotListener가 감지하여 화면 자동 전환
                                        if (recommendations.isNotEmpty()) {
                                            updateRoomInFirestore(roomId, mapOf("suggestedRoutes" to recommendations)) {
                                                Log.e("Nav", "추천 경로 저장 실패: $it")
                                            }
                                        } else {
                                            Toast.makeText(navController.context, "추천할 장소를 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        // 추천 경로(suggestedRoutes)가 있으면 -> 투표 화면 (VoteScreen)
                        val isHost = roomDetailState!!.members.find { it.user.id == currentUser.id }?.isHost ?: false
                        val roomId = backStackEntry.arguments?.getString("roomId") ?: ""

                        VoteScreen(
                            navController = navController,
                            roomId = nonNullRoomId,
                            loggedInUser = currentUser,
                            members = roomDetailState!!.members,
                            routes = roomDetailState!!.suggestedRoutes,
                            isHost = isHost,

                            // [투표 로직]
                            onVote = { routeId, userId ->
                                // 현재 경로 리스트 복사 및 수정
                                val updatedRoutes = roomDetailState!!.suggestedRoutes.map { route ->
                                    if (route.id == routeId) {
                                        // 이미 투표했으면 제거, 아니면 추가
                                        val newVoters = if (userId in route.voters) {
                                            route.voters - userId
                                        } else {
                                            (route.voters + userId).distinct()
                                        }
                                        route.copy(voters = newVoters)
                                    } else {
                                        route
                                    }
                                }
                                // Firestore에 업데이트
                                updateRoomInFirestore(roomId, mapOf("suggestedRoutes" to updatedRoutes)) {}
                            },

                            // [투표 완료 상태 토글]
                            onVoteComplete = { userId ->
                                val updatedMember = roomDetailState!!.members.find { it.user.id == userId }?.copy(voted = true)
                                if (updatedMember != null) {
                                    updateMemberInFirestore(nonNullRoomId, updatedMember) { _ -> }
                                }
                            },

                            // ★ [최종 선택] 방장이 확정 버튼 눌렀을 때
                            onFinalSelect = { routeId ->
                                val selectedRoute = roomDetailState!!.suggestedRoutes.find { it.id == routeId }
                                if (selectedRoute != null) {
                                    // 1. 목적지를 선택된 장소로 설정
                                    // 2. 추천 리스트 비우기 (-> 다시 RoomDetailScreen으로 돌아감)
                                    val updates: MutableMap<String, Any> = mutableMapOf(
                                        "destination" to selectedRoute.placeName,
                                        "destX" to selectedRoute.longitude,
                                        "destY" to selectedRoute.latitude
                                    )

                                    updates["suggestedRoutes"] = emptyList<Any>()
                                    updates["isCalculating"] = false

                                    updateRoomInFirestore(roomId, updates) {}
                                }
                            },

                            // 🔹 [새로 추가] 투표 화면에서 "뒤로가기" 했을 때 처리
                            onBackToRoom = {
                                val current = roomDetailState
                                if (current != null) {
                                    // 1) 로컬 state 리셋
                                    val resetMembers = current.members.map { it.copy(voted = false) }
                                    roomDetailState = current.copy(
                                        suggestedRoutes = emptyList(),
                                        members = resetMembers
                                    )

                                    // 2) Firestore에 투표 추천 목록 & 계산 상태 & voted 플래그 리셋
                                    val updates: MutableMap<String, Any> = mutableMapOf(
                                        "suggestedRoutes" to emptyList<Any>(),
                                        "isCalculating" to false
                                    )
                                    updateRoomInFirestore(roomId, updates) {}

                                    resetMembers.forEach { m ->
                                        updateMemberInFirestore(roomId, m) {}
                                    }
                                }
                            }
                        )
                    }
                } else {
                    androidx.compose.material3.Text("방 정보를 불러오는 중...")
                }
            }
        }

        // [수정됨] 지도 선택 화면 연결
        composable(
            // 라우트 주소: 좌표와 장소 이름을 쿼리 파라미터(?key=value)로 받음
            route = "${AppDestinations.MAP_SELECTION_SCREEN}/{type}/{memberIndex}?roomId={roomId}&lat={lat}&lng={lng}&placeName={placeName}",

            arguments = listOf(
                navArgument("roomId") { nullable = true }, // nullable = true -> 필수 아님
                // ★ [핵심 수정] FloatType -> StringType으로 변경 (정밀도 유지 및 안전한 전달)
                navArgument("lat") { type = NavType.StringType; nullable = true },
                navArgument("lng") { type = NavType.StringType; nullable = true },
                navArgument("placeName") { nullable = true }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val memberIndex = backStackEntry.arguments?.getString("memberIndex")?.toInt() ?: -1
            val roomId = backStackEntry.arguments?.getString("roomId")

            // ★ [핵심 수정] String으로 받아서 -> Double로 안전하게 변환
            // 값이 없거나 변환 실패하면 0.0이 들어감 -> MapScreen에서 초기화 로직 안 돔 -> 안전함
            val latStr = backStackEntry.arguments?.getString("lat")
            val lngStr = backStackEntry.arguments?.getString("lng")

            val lat = latStr?.toDoubleOrNull() ?: 0.0
            val lng = lngStr?.toDoubleOrNull() ?: 0.0
            val placeName = backStackEntry.arguments?.getString("placeName")

            val currentUser = loggedInState.currentUser

            MapSelectionScreen(
                // 수정된 값 전달
                initialLat = lat,
                initialLng = lng,
                initialPlaceName = placeName,

                onLocationSelected = { selectedName, latLng ->
                    val selectedLat = latLng?.latitude ?: 0.0
                    val selectedLng = latLng?.longitude ?: 0.0

                    if (roomId != null && currentUser != null && roomDetailState != null) {
                        when (type) {
                            "destination" -> {
                                // 서버 저장
                                val updates = hashMapOf<String, Any>(
                                    "destination" to selectedName,
                                    "destY" to selectedLat,
                                    "destX" to selectedLng
                                )
                                updateRoomInFirestore(roomId, updates) {
                                    Log.e("MAP_DEBUG", "목적지 서버 저장 실패: $it")
                                }

                                roomDetailState = roomDetailState!!.copy(
                                    destination = selectedName,
                                    destX = selectedLng,
                                    destY = selectedLat
                                )
                                Log.e("MAP_DEBUG", "목적지 로컬 화면 갱신 완료")
                            }

                            "startPoint" -> {
                                val myMemberInfo = roomDetailState!!.members.find { it.user.id == currentUser.id }

                                if (myMemberInfo != null) {
                                    val newMemberInfo = myMemberInfo.copy(
                                        startPoint = selectedName,
                                        x = selectedLng,
                                        y = selectedLat,
                                        isReady = false
                                    )
                                    // 서버 저장
                                    updateMemberInFirestore(roomId, newMemberInfo) {
                                        Log.e("MAP_DEBUG", "출발지 서버 저장 실패: $it")
                                    }

                                    val updatedMembers = roomDetailState!!.members.map {
                                        if (it.user.id == currentUser.id) newMemberInfo else it
                                    }
                                    roomDetailState = roomDetailState!!.copy(members = updatedMembers)

                                    Log.e("MAP_DEBUG", "3. 출발지 로컬 화면 갱신 완료")
                                } else {
                                    Log.e("MAP_DEBUG", "내 정보를 찾을 수 없음")
                                }
                            }
                        }
                    }
                    else {
                        if (type == "destination") {
                            onNonLoggedInStateChange(
                                nonLoggedInState.copy(
                                    destination = selectedName,
                                    destX = selectedLng,
                                    destY = selectedLat
                                )
                            )
                        } else if (type == "startPoint" && memberIndex != -1) {
                            val updatedMembers = nonLoggedInState.members.toMutableList()
                            if (memberIndex < updatedMembers.size) {
                                updatedMembers[memberIndex] = updatedMembers[memberIndex].copy(
                                    startPoint = selectedName,
                                    x = selectedLng,
                                    y = selectedLat
                                )
                                onNonLoggedInStateChange(nonLoggedInState.copy(members = updatedMembers))
                            }
                        }
                    }

                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(AppDestinations.RESULT_SCREEN) {
            // ★ [수정] 이제 더미 데이터 대신 진짜 저장소와 State를 넘깁니다.

            // 현재 방 정보와 방장 여부 확인
            val currentRoomId = roomDetailState?.roomId
            val isHost = roomDetailState?.members?.find { it.user.id == loggedInState.currentUser?.id }?.isHost == true

            ResultScreen(
                navController = navController,
                repository = repository,
                gachigaState = nonLoggedInState,

                // 뒤로 가기 눌렀을 때 행동
                onBackToEdit = {
                    // 방장이라면 Firebase에 계산 끝났다(false)고 알림
                    if (currentRoomId != null) {
                        updateRoomInFirestore(currentRoomId, mapOf("isCalculating" to false)) {}
                    }
                    navController.popBackStack()
                },
                // 1순위: 로그인한 유저 ID (Firebase)
                // 2순위: 없으면 그냥 멤버 리스트의 첫 번째 사람을 '나'로 간주 (비로그인 테스트용)
                // 아래 주석 지우면 로그인/비로그인 모두 "내 경로만 자세히 보기" 가능
                currentUserId = loggedInState.currentUser?.id?.hashCode()//?: nonLoggedInState.members.firstOrNull()?.id
            )
        }
    }
}


// 1. 카카오 토큰으로 Firebase 함수 호출 (커스텀 토큰 발급)
private fun getFirebaseCustomToken(kakaoAccessToken: String, onSuccess: (String) -> Unit) {
    Log.e("DEBUG_TAG", "1. getFirebaseCustomToken 함수 시작됨")
    val functions = Firebase.functions("asia-northeast3")
    val data = hashMapOf("token" to kakaoAccessToken)

    functions
        .getHttpsCallable("verifyKakaoToken")
        .call(data)
        .addOnSuccessListener { result ->
            val dataMap = result.data as? Map<String, Any>
            val firebaseToken = dataMap?.get("firebaseToken") as? String
            if (firebaseToken != null) {
                onSuccess(firebaseToken)
            } else {
                Log.e("DEBUG_TAG", "🚨 토큰 비어있음")
            }
        }
        .addOnFailureListener { e ->
            Log.e("DEBUG_TAG", "🚨 서버 통신 실패", e)
        }
}

// 2. 발급받은 토큰으로 Firebase 로그인
private fun signInToFirebaseWithCustomToken(firebaseToken: String, onSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit) {
    Firebase.auth.signInWithCustomToken(firebaseToken)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = Firebase.auth.currentUser
                if (user != null) onSuccess(user)
            } else {
                Log.e("DEBUG_TAG", "🚨 Firebase 로그인 실패", task.exception)
            }
        }
}

// 3. 방 생성 함수 (랜덤 코드 생성 포함)
fun createRoomInFirestore(
    hostUser: User,
    onSuccess: (String, String) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    // 6자리 랜덤 코드 생성
    val newRoomId = (1..6).map { ('A'..'Z') + ('0'..'9') }.map { it.random() }.joinToString("")

    val inviteLink = "https://gachiga.app/join?roomId=$newRoomId"

    val initialMember = RoomMember(
        user = hostUser,
        isHost = true,
        isReady = false,
        startPoint = "위치 선택 전"
    )

    val roomData = hashMapOf(
        "roomId" to newRoomId,
        "invitationCode" to newRoomId,
        "inviteLink" to inviteLink,
        "createdAt" to System.currentTimeMillis(),
        "destination" to "미설정",
        "arrivalTime" to "14:00",
        "members" to listOf(initialMember)
    )

    db.collection("rooms").document(newRoomId)
        .set(roomData)
        .addOnSuccessListener { onSuccess(newRoomId, inviteLink) }
        .addOnFailureListener { onFailure(it) }
}

// 4. 방 정보 수정 함수 (목적지, 시간 등)
fun updateRoomInFirestore(
    roomId: String,
    updatedData: Map<String, Any>,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    db.collection("rooms").document(roomId)
        .update(updatedData)
        .addOnFailureListener { onFailure(it) }
}

// 5. 방 참여 함수 (중복 체크 포함)
fun joinRoomInFirestore(
    roomId: String,
    joinUser: User,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    val roomRef = db.collection("rooms").document(roomId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(roomRef)
        if (!snapshot.exists()) throw Exception("방이 없습니다.")

        // 중복 참여 방지
        val currentMembers = snapshot.toObject(RoomDetail::class.java)?.members ?: emptyList()
        val isAlreadyJoined = currentMembers.any { it.user.id == joinUser.id }
        if (isAlreadyJoined) return@runTransaction

        val newMember = RoomMember(
            user = joinUser,
            isHost = false,
            isReady = false,
            startPoint = "위치 선택 전"
        )
        transaction.update(roomRef, "members", FieldValue.arrayUnion(newMember))
    }.addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onFailure(it) }
}

// 6. 멤버 정보 수정 함수 (내 정보만 수정)
fun updateMemberInFirestore(
    roomId: String,
    updatedMember: RoomMember,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    val roomRef = db.collection("rooms").document(roomId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(roomRef)
        val currentMembers = snapshot.toObject(RoomDetail::class.java)?.members ?: return@runTransaction

        val newMemberList = currentMembers.map { member ->
            if (member.user.id == updatedMember.user.id) updatedMember else member
        }
        transaction.update(roomRef, "members", newMemberList)
    }.addOnFailureListener { onFailure(it) }
}

// 7. 방 나가기 함수
fun leaveRoomInFirestore(
    roomId: String,
    leaveUser: User,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    val roomRef = db.collection("rooms").document(roomId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(roomRef)
        val currentMembers = snapshot.toObject(RoomDetail::class.java)?.members ?: return@runTransaction

        // 방장이 나가려고 하면 막음 (방장이 나가면 방 자체가 사라져야 하므로)
        if (currentMembers.find { it.user.id == leaveUser.id }?.isHost == true) {
            throw Exception("방장은 뒤로가기로 나갈 수 없습니다.")
        }

        // 나가는 멤버를 제외하고 새로운 리스트 생성 - Compose 화면에서 자동으로 번호가 앞당겨지는 효과
        val newMemberList = currentMembers.filter { it.user.id != leaveUser.id }

        // 새로운 리스트로 덮어쓰기
        transaction.update(roomRef, "members", newMemberList)

    }.addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onFailure(it) }
}

// 8. 방장이 나갈 때 다음 멤버에게 방장 권한을 위임하고 나가는 트랜잭션 함수
fun transferHostAndLeaveRoomInFirestore(
    roomId: String,
    oldHostUser: User,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    val roomRef = db.collection("rooms").document(roomId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(roomRef)
        val roomDetail = snapshot.toObject(RoomDetail::class.java)
        val currentMembers = roomDetail?.members ?: throw Exception("방 정보를 찾을 수 없습니다.")

        // 기존 방장을 제외한 리스트
        val remainingMembers = currentMembers.filter { it.user.id != oldHostUser.id }

        // 다음 방장을 찾음 (나가는 사람 제외 첫 번째 멤버)
        val newHost = remainingMembers.firstOrNull() ?: throw Exception("방에 남은 멤버가 없습니다. 방을 제거합니다.")

        // 새 리스트 생성: 새 방장의 isHost를 true로 설정
        val updatedMembers = remainingMembers.map { member ->
            if (member.user.id == newHost.user.id) {
                // 새로운 방장으로
                member.copy(isHost = true)
            } else {
                // 나머지 멤버는 유지
                member
            }
        }

        // Firebase 업데이트 (새 멤버 리스트로 덮어쓰기)
        transaction.update(roomRef, "members", updatedMembers)

    }.addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onFailure(it) }
}

// 9. 방 자체를 삭제하는 함수(방장 혼자 남았을 때 사용)
fun deleteRoomInFirestore(
    roomId: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    db.collection("rooms").document(roomId)
        .delete()
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onFailure(it) }
}