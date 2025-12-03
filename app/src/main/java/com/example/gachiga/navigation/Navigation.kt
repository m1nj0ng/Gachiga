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
import androidx.navigation.navArgument
import com.kakao.vectormap.LatLng
import com.google.firebase.firestore.SetOptions

object AppDestinations {
    const val START_SCREEN = "start"
    const val LOBBY_SCREEN = "lobby"
    const val INPUT_SCREEN = "input"
    const val MAP_SELECTION_SCREEN = "map_selection"
    const val ROOM_DETAIL_SCREEN = "room_detail/{roomId}"
    const val RESULT_SCREEN = "result"
}

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
                            onSuccess = { createdRoomId ->
                                if (createdRoomId.isNotBlank()) {
                                    Log.d("Navigation", "방 생성 성공! ID: $createdRoomId")
                                    
                                    val hostMember = RoomMember(user = currentUser, isHost = true)
                                    roomDetailState = newRoom.copy(
                                        roomId = createdRoomId,
                                        invitationCode = createdRoomId,
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
                onJoinRoom = { inputCode -> // 추가: 참여하기 함수 호출
                    val currentUser = loggedInState.currentUser
                    if (currentUser != null) {
                        joinRoomInFirestore(
                            roomId = inputCode,
                            joinUser = currentUser,
                            onSuccess = {
                                navController.navigate("room_detail/$inputCode")
                            },
                            onFailure = { Log.e("Lobby", "참여 실패: ${it.message}") }
                        )
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

        composable(
            route = AppDestinations.ROOM_DETAIL_SCREEN,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            val currentUser = loggedInState.currentUser

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

                                    // 2. isCalculating  켜졌는지 확인!
                                    if (roomData.isCalculating) {

                                        // 데이터를 GachigaState으로 변환
                                        val convertedMembers = roomData.members.map { roomMember ->
                                            com.example.gachiga.data.Member(
                                                id = roomMember.user.id.hashCode(),
                                                name = roomMember.user.nickname,
                                                startPoint = roomMember.startPoint,
                                                x = roomMember.x, // x (경도)
                                                y = roomMember.y, // y (위도)
                                                placeName = roomMember.startPoint,
                                                mode = roomMember.travelMode,
                                                color = -16776961
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
                                    }
                                }
                            }
                        }
                }

                if (roomDetailState != null) {
                    if (roomDetailState!!.suggestedRoutes.isEmpty()) {
                        RoomDetailScreen(
                            navController = navController,
                            loggedInUser = currentUser,
                            roomDetail = roomDetailState!!,

                            // 방 정보 저장 (기존 코드 유지)
                            onStateChange = { updatedRoom ->
                                val isHost = roomDetailState!!.members.find {
                                    it.user.id == currentUser.id
                                }?.isHost == true

                                if (isHost) {
                                    val updates = mutableMapOf<String, Any>(
                                        "destination" to updatedRoom.destination,
                                        "arrivalTime" to updatedRoom.arrivalTime
                                    )
                                    if (updatedRoom.destY != 0.0 && updatedRoom.destX != 0.0) {
                                        updates["destY"] = updatedRoom.destY
                                        updates["destX"] = updatedRoom.destX
                                    }
                                    updateRoomInFirestore(roomId, updates) {}
                                    roomDetailState = updatedRoom
                                }
                            },

                            // 멤버 정보 저장 (기존 코드 유지)
                            onMemberUpdate = { updatedMember ->
                                updateMemberInFirestore(roomId, updatedMember) {}
                            },

                            // 계산 버튼 클릭 시
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
                            }
                        )
                    } else {
                        // (투표 화면 등 기존 코드 유지)
                        val isHost = roomDetailState!!.members.find { it.user.id == currentUser.id }?.isHost ?: false
                        VoteScreen(
                            navController = navController,
                            loggedInUser = currentUser,
                            members = roomDetailState!!.members,
                            routes = roomDetailState!!.suggestedRoutes,
                            isHost = isHost,
                            onVote = { routeId, userId -> /*...*/ },
                            onVoteComplete = { userId -> /*...*/ },
                            onFinalSelect = { routeId -> /*...*/ }
                        )
                    }
                } else {
                    androidx.compose.material3.Text("방 정보를 불러오는 중...")
                }
            }
        }

        // ★ [핵심 수정] 지도 선택 화면에서 좌표(LatLng)를 받아와서 State에 저장
        // 지도 선택 화면 (디버깅 로그 & 강제 UI 업데이트 추가)
        composable(
            "${AppDestinations.MAP_SELECTION_SCREEN}/{type}/{memberIndex}?roomId={roomId}",
            arguments = listOf(navArgument("roomId") { nullable = true })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val memberIndex = backStackEntry.arguments?.getString("memberIndex")?.toInt() ?: -1
            val roomId = backStackEntry.arguments?.getString("roomId")
            val currentUser = loggedInState.currentUser

            MapSelectionScreen(
                onLocationSelected = { selectedName, latLng ->

                    val lat = latLng?.latitude ?: 0.0
                    val lng = latLng?.longitude ?: 0.0

                    if (roomId != null && currentUser != null && roomDetailState != null) {
                        when (type) {
                            "destination" -> {
                                // 서버 저장
                                val updates = hashMapOf<String, Any>(
                                    "destination" to selectedName,
                                    "destY" to lat,
                                    "destX" to lng
                                )
                                updateRoomInFirestore(roomId, updates) {
                                    Log.e("MAP_DEBUG", "목적지 서버 저장 실패: $it")
                                }

                                roomDetailState = roomDetailState!!.copy(
                                    destination = selectedName,
                                    destX = lng,
                                    destY = lat
                                )
                                Log.e("MAP_DEBUG", "목적지 로컬 화면 갱신 완료")
                            }

                            "startPoint" -> {
                                val myMemberInfo = roomDetailState!!.members.find { it.user.id == currentUser.id }

                                if (myMemberInfo != null) {
                                    val newMemberInfo = myMemberInfo.copy(
                                        startPoint = selectedName,
                                        x = lng,
                                        y = lat,
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
                                    destX = lng,
                                    destY = lat
                                )
                            )
                        } else if (type == "startPoint" && memberIndex != -1) {
                            val updatedMembers = nonLoggedInState.members.toMutableList()
                            if (memberIndex < updatedMembers.size) {
                                updatedMembers[memberIndex] = updatedMembers[memberIndex].copy(
                                    startPoint = selectedName,
                                    x = lng,
                                    y = lat
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
            ResultScreen(
                navController = navController,
                repository = repository,
                gachigaState = nonLoggedInState
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
    onSuccess: (String) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    // 6자리 랜덤 코드 생성
    val newRoomId = (1..6).map { ('A'..'Z') + ('0'..'9') }.map { it.random() }.joinToString("")

    val initialMember = RoomMember(
        user = hostUser,
        isHost = true,
        isReady = false,
        startPoint = "위치 선택 전"
    )

    val roomData = hashMapOf(
        "roomId" to newRoomId,
        "invitationCode" to newRoomId,
        "createdAt" to System.currentTimeMillis(),
        "destination" to "미설정",
        "arrivalTime" to "14:00",
        "members" to listOf(initialMember)
    )

    db.collection("rooms").document(newRoomId)
        .set(roomData)
        .addOnSuccessListener { onSuccess(newRoomId) }
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