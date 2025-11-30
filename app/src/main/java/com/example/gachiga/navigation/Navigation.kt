package com.example.gachiga.navigation

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.copy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.gachiga.data.*
import com.example.gachiga.ui.input.InputScreen
import com.example.gachiga.ui.map.MapSelectionScreen
import com.example.gachiga.ui.start.StartScreen
import com.example.gachiga.data.LoggedInState
import com.example.gachiga.data.RoomDetail
import com.example.gachiga.data.RoomMember
import com.example.gachiga.ui.lobby.LobbyScreen
import com.example.gachiga.ui.room.RoomDetailScreen
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import com.google.firebase.Firebase
import com.kakao.sdk.user.UserApiClient
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.kakao.vectormap.LatLng
import com.google.firebase.firestore.SetOptions

// 로그인 관련 경로 제거
object AppDestinations {
    const val START_SCREEN = "start"
    const val LOBBY_SCREEN = "lobby"
    const val INPUT_SCREEN = "input"
    const val MAP_SELECTION_SCREEN = "map_selection"
    const val CREATE_ROOM_SCREEN = "create_room"
    const val ROOM_DETAIL_SCREEN = "room_detail/{roomId}"
}

@Composable
fun GachigaApp(
    navController: NavHostController,
    nonLoggedInState: GachigaState,
    loggedInState: LoggedInState,
    onNonLoggedInStateChange: (GachigaState) -> Unit,
    onLoggedInStateChange: (LoggedInState) -> Unit
) {
    var roomDetailState by remember { mutableStateOf<RoomDetail?>(null) }
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = AppDestinations.START_SCREEN) {

        composable(AppDestinations.START_SCREEN) {
            StartScreen(
                onNavigateToLogin = { kakaoAccessToken -> // StartScreen에서 토큰을 받아옴
                    getFirebaseCustomToken(kakaoAccessToken) { firebaseCustomToken ->

                        signInToFirebaseWithCustomToken(firebaseCustomToken) { firebaseUser ->
                            // 2. Firebase 로그인 성공 -> 사용자 정보 가져오기
                            UserApiClient.instance.me { user, error ->
                                if (user != null) {
                                    onLoggedInStateChange(
                                        loggedInState.copy(
                                            currentUser = User(
                                                id = firebaseUser.uid, // Firebase UID 저장
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
                    // "로그인 없이 진행" 버튼 클릭 시, 비로그인 입력 화면으로 이동
                    navController.navigate(AppDestinations.INPUT_SCREEN)
                }
            )
        }

        // 로그인 후 보일 로비 화면
        composable(AppDestinations.LOBBY_SCREEN) {
            LobbyScreen(
                navController = navController,
                state = loggedInState,
                onRoomCreated = { newRoom ->
                    val currentUser = loggedInState.currentUser

                    if (currentUser != null) {
                        // 1. 서버(Firestore)에 저장을 먼저 시도
                        createRoomInFirestore(
                            hostUser = currentUser,
                            roomName = newRoom.roomName,
                            onSuccess = { createdRoomId ->
                                // 2. 서버 저장에 성공하면 그때 화면을 이동

                                val hostMember = RoomMember(user = currentUser, isHost = true)
                                roomDetailState = newRoom.copy(
                                    // 화면에 보여줄 때도 이 코드를 사용
                                    roomId = createdRoomId,
                                    members = listOf(hostMember)
                                )

                                // (방 상세 화면으로 이동)
                                navController.navigate("room_detail/$createdRoomId")
                            },
                            onFailure = {
                                Log.e("Lobby", "방 만들기 실패!")
                            }
                        )
                    }
                },
                onJoinRoom = { inputCode ->
                    val currentUser = loggedInState.currentUser

                    if (currentUser != null) {
                        joinRoomInFirestore(
                            roomId = inputCode, // 사용자가 입력한 코드
                            joinUser = currentUser,
                            onSuccess = {
                                // 2. 성공하면 바로 그 방으로 이동
                                navController.navigate("room_detail/$inputCode")
                            },
                            onFailure = {
                                Log.e("Lobby", "참여 실패: ${it.message}")
                            }
                        )
                    }
                }
            )
        }

        // 비로그인 시 보일 입력 화면
        composable(AppDestinations.INPUT_SCREEN) {
            InputScreen(
                navController = navController,
                gachigaState = nonLoggedInState,
                onStateChange = onNonLoggedInStateChange
            )
        }

        // 약속 방 상세 화면 (RoomDetailScreen)
        composable(AppDestinations.ROOM_DETAIL_SCREEN) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            val currentUser = loggedInState.currentUser

            if (roomId != null && currentUser != null) {

                // Firestore 실시간 감시
                androidx.compose.runtime.LaunchedEffect(roomId) {
                    val db = Firebase.firestore
                    db.collection("rooms").document(roomId)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.e("Firestore", "감시 실패", e)
                                return@addSnapshotListener
                            }

                            // 데이터가 있고, 내용이 바뀌면
                            if (snapshot != null && snapshot.exists()) {
                                val roomData = snapshot.toObject(RoomDetail::class.java)
                                if (roomData != null) {
                                    roomDetailState = roomData // 화면 갱신
                                }
                            }
                        }
                }

                // 화면 그리기
                if (roomDetailState != null) {
                    RoomDetailScreen(
                        navController = navController,
                        loggedInUser = currentUser,
                        roomDetail = roomDetailState!!,

                        // 사용자가 화면에서 뭔가를 바꿨을 때 (예: 목적지 변경)
                        // 1. 방 정보(목적지, 시간)가 바뀌었을 때
                        onStateChange = { updatedRoom ->
                            val updates = hashMapOf<String, Any>(
                                "destination" to updatedRoom.destination,
                                "arrivalTime" to updatedRoom.arrivalTime
                            )
                            updateRoomInFirestore(roomId, updates) {}

                            roomDetailState = updatedRoom
                        },

                        // 2. 멤버 상태가 바뀌었을 때
                        onMemberUpdate = { updatedMember ->
                            // 서버에 내 정보만 안전하게 교체
                            updateMemberInFirestore(roomId, updatedMember) {
                                Log.e("Navigation", "멤버 상태 저장 실패")
                            }
                        }
                    )
                } else {
                    // 데이터를 불러오는 동안 보여줄 화면
                    androidx.compose.material3.Text("방 정보를 불러오는 중...")
                }
            }
        }

        // 지도 선택 화면
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

                    // 로그인 상태
                    if (roomId != null && currentUser != null && roomDetailState != null) {
                        when (type) {
                            "destination" -> {
                                // 목적지 업데이트
                                val updates = hashMapOf<String, Any>(
                                    "destination" to selectedName,
                                    "destLat" to lat,
                                    "destLng" to lng
                                )
                                updateRoomInFirestore(roomId, updates) {
                                    Log.e("Map", "목적지 저장 실패")
                                }
                            }

                            "startPoint" -> {
                                val myMemberInfo = roomDetailState!!.members.find { it.user.id == currentUser.id }

                                if (myMemberInfo != null) {
                                    val newMemberInfo = myMemberInfo.copy(
                                        startPoint = selectedName,
                                        startLat = lat,
                                        startLng = lng,
                                        isReady = false
                                    )
                                    updateMemberInFirestore(roomId, newMemberInfo) {
                                        Log.e("Map", "출발지 저장 실패")
                                    }
                                }
                            }
                        }
                    }
                    // 2. 비로그인
                    else {
                        if (type == "destination") {
                            onNonLoggedInStateChange(nonLoggedInState.copy(destination = selectedName))
                        } else if (type == "startPoint" && memberIndex != -1) {
                            val updatedMembers = nonLoggedInState.members.toMutableList()
                            if (memberIndex < updatedMembers.size) {
                                updatedMembers[memberIndex] = updatedMembers[memberIndex].copy(startPoint = selectedName)
                                onNonLoggedInStateChange(nonLoggedInState.copy(members = updatedMembers))
                            }
                        }
                    }
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}



// 카카오 토큰으로 Firebase 함수를 호출하는 함수
private fun getFirebaseCustomToken(kakaoAccessToken: String, onSuccess: (String) -> Unit) {
    Log.e("DEBUG_TAG", "1. getFirebaseCustomToken 함수 시작됨")

    val functions = Firebase.functions("asia-northeast3")

    val data = hashMapOf("token" to kakaoAccessToken)

    functions
        .getHttpsCallable("verifyKakaoToken")
        .call(data)
        .addOnSuccessListener { result ->
            // 성공했을 때
            Log.e("DEBUG_TAG", "2. 서버 응답 도착함!")

            val dataMap = result.data as? Map<String, Any>
            val firebaseToken = dataMap?.get("firebaseToken") as? String

            if (firebaseToken != null) {
                Log.e("DEBUG_TAG", "3. 토큰 추출 성공: ${firebaseToken.take(10)}...")
                onSuccess(firebaseToken)
            } else {
                Log.e("DEBUG_TAG", "3. 서버 응답은 왔는데 firebaseToken이 비어있음! 응답내용: ${result.data}")
            }
        }
        .addOnFailureListener { e ->
            // 실패했을 때
            Log.e("DEBUG_TAG", "2. 서버 통신 실패", e)
        }
}

// 2. 받은 커스텀 토큰으로 Firebase에 최종 로그인 (디버깅 버전)
private fun signInToFirebaseWithCustomToken(firebaseToken: String, onSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit) {
    Log.e("DEBUG_TAG", "4. Firebase 로그인 시도 시작")

    Firebase.auth.signInWithCustomToken(firebaseToken)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = Firebase.auth.currentUser
                Log.e("DEBUG_TAG", "5. Firebase 로그인 최종 성공! UID: ${user?.uid}")
                if (user != null) {
                    onSuccess(user)
                }
            } else {
                Log.e("DEBUG_TAG", "5. Firebase 로그인 실패 (토큰은 맞는데 로그인이 안됨)", task.exception)
            }
        }
}

// 3. Firestore에 방을 저장하는 함수
fun createRoomInFirestore(
    hostUser: User,
    roomName: String,
    onSuccess: (String) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
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
        "roomName" to roomName,
        "createdAt" to System.currentTimeMillis(),
        "destination" to "미설정",
        "arrivalTime" to "14:00",
        "members" to listOf(initialMember)
    )

    // 받아온 roomId를 이름으로 사용하여 저장
    db.collection("rooms").document(newRoomId)
        .set(roomData)
        .addOnSuccessListener {
            Log.d("Firestore", "방 생성 성공! ID: $newRoomId")
            onSuccess(newRoomId)
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "방 생성 실패", e)
            onFailure(e)
        }
}

// 4. 방 정보를 수정 함수 - 목적지, 시간 바꿀 때
fun updateRoomInFirestore(
    roomId: String,
    updatedData: Map<String, Any>, // 바꿀 내용만 담은 보따리
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore

    db.collection("rooms").document(roomId)
        .update(updatedData)
        .addOnFailureListener { e ->
            Log.e("Firestore", "업데이트 실패", e)
            onFailure(e)
        }
}

// 5. 방 참여 함수
fun joinRoomInFirestore(
    roomId: String,       // 사용자가 입력한 6자리 코드
    joinUser: User,       // 참여하는 나(친구)
    onSuccess: () -> Unit, // 성공하면 실행
    onFailure: (Exception) -> Unit // 실패하면 실행
) {
    val db = Firebase.firestore
    val roomRef = db.collection("rooms").document(roomId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(roomRef)

        if (!snapshot.exists()) {
            throw Exception("존재하지 않는 방입니다. 코드를 다시 확인해주세요.")
        }

        // 새 멤버 데이터 만들기
        val newMember = RoomMember(
            user = joinUser,
            isHost = false, // 친구는 방장이 아님
            isReady = false,
            startPoint = "위치 선택 전"
        )

        // 기존 명단에 추가 (중복된 사람이면 추가 안 함)
        transaction.update(roomRef, "members", FieldValue.arrayUnion(newMember))
    }.addOnSuccessListener {
        Log.d("Firestore", "방 참여 성공!")
        onSuccess()
    }.addOnFailureListener { e ->
        Log.e("Firestore", "방 참여 실패", e)
        onFailure(e)
    }
}

// 6. 내 정보만 안전하게 수정 (트랜잭션)
fun updateMemberInFirestore(
    roomId: String,
    updatedMember: RoomMember, // 수정된 내 정보
    onFailure: (Exception) -> Unit
) {
    val db = Firebase.firestore
    val roomRef = db.collection("rooms").document(roomId)

    db.runTransaction { transaction ->
        // 1. 서버에서 가장 최신 정보 가져옴
        val snapshot = transaction.get(roomRef)
        val currentMembers = snapshot.toObject(RoomDetail::class.java)?.members ?: return@runTransaction

        // 2. 명단에서 교체
        val newMemberList = currentMembers.map { member ->
            if (member.user.id == updatedMember.user.id) {
                updatedMember // 내 정보는 새로운 걸로 교체!
            } else {
                member // 다른 사람 정보는 건드리지 않고 그대로 유지
            }
        }

        // 3. 수정된 명단 서버에 저장
        transaction.update(roomRef, "members", newMemberList)
    }.addOnFailureListener { e ->
        Log.e("Firestore", "멤버 업데이트 실패", e)
        onFailure(e)
    }
}