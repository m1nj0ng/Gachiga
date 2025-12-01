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
                onNavigateToLogin = {
                    UserApiClient.instance.me { user, error ->
                        if (error != null) {
                            Log.e("KAKAO_USER_INFO", "사용자 정보 요청 실패", error)
                        } else if (user != null) {
                            onLoggedInStateChange(
                                loggedInState.copy(
                                    currentUser = User(
                                        id = user.id.toString(),
                                        nickname = user.kakaoAccount?.profile?.nickname ?: "사용자",
                                        profileImageUrl = user.kakaoAccount?.profile?.thumbnailImageUrl ?: ""
                                    )
                                )
                            )
                            navController.navigate(AppDestinations.LOBBY_SCREEN) {
                                popUpTo(AppDestinations.START_SCREEN) { inclusive = true }
                            }
                        }
                    }
                },
                onNavigateToInput = {
                    navController.navigate(AppDestinations.INPUT_SCREEN)
                }
            )
        }

        composable(AppDestinations.LOBBY_SCREEN) {
            LobbyScreen(
                navController = navController,
                state = loggedInState,
                onRoomCreated = { newRoom ->
                    loggedInState.currentUser?.let { user ->
                        val hostMember = RoomMember(user = user, isHost = true)
                        roomDetailState = newRoom.copy(members = listOf(hostMember))
                        navController.navigate("room_detail/${newRoom.roomId}")
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

        composable(AppDestinations.ROOM_DETAIL_SCREEN) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            if (roomDetailState != null && roomDetailState!!.roomId == roomId) {
                if (roomDetailState!!.suggestedRoutes.isEmpty()) {
                    RoomDetailScreen(
                        navController = navController,
                        loggedInUser = loggedInState.currentUser!!,
                        roomDetail = roomDetailState!!,
                        onStateChange = { roomDetailState = it },
                        onCalculate = {
                            // ★ 로그인 버전 계산 로직은 나중에 구현 (투표 기능 관련)
                            // 현재는 STEP 3에서 비로그인 버전부터 완성할 예정입니다.
                        }
                    )
                } else {
                    val isHost = roomDetailState!!.members.find { it.user.id == loggedInState.currentUser!!.id }?.isHost ?: false
                    VoteScreen(
                        navController = navController,
                        loggedInUser = loggedInState.currentUser!!,
                        members = roomDetailState!!.members,
                        routes = roomDetailState!!.suggestedRoutes,
                        isHost = isHost,
                        onVote = { routeId, userId ->
                            // 투표 로직 (기존 유지)
                            val updatedRoutes = roomDetailState!!.suggestedRoutes.map { route ->
                                if (route.id == routeId) {
                                    val newVoters = if (userId in route.voters) route.voters - userId else (route.voters + userId).distinct()
                                    route.copy(voters = newVoters)
                                } else route
                            }
                            roomDetailState = roomDetailState!!.copy(suggestedRoutes = updatedRoutes)
                        },
                        onVoteComplete = { userId ->
                            val updatedMembers = roomDetailState!!.members.map {
                                if (it.user.id == userId) it.copy(voted = true) else it
                            }
                            roomDetailState = roomDetailState!!.copy(members = updatedMembers)
                        },
                        onFinalSelect = { routeId ->
                            val finalPlaceName = roomDetailState!!.suggestedRoutes.find { it.id == routeId }?.placeName
                            roomDetailState = roomDetailState!!.copy(finalPlace = finalPlaceName)
                        }
                    )
                }
            }
        }

        // ★ [핵심 수정] 지도 선택 화면에서 좌표(LatLng)를 받아와서 State에 저장
        composable(
            route = "${AppDestinations.MAP_SELECTION_SCREEN}/{type}/{memberIndex}?roomId={roomId}",
            arguments = listOf(navArgument("roomId") { nullable = true })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val memberIndex = backStackEntry.arguments?.getString("memberIndex")?.toInt() ?: -1
            val roomId = backStackEntry.arguments?.getString("roomId")

            MapSelectionScreen(
                onLocationSelected = { selectedName, latLng -> // ★ LatLng 파라미터 추가됨
                    when (type) {
                        "destination" -> {
                            if (roomId != null && roomDetailState != null) {
                                // [로그인] 목적지 좌표 저장
                                roomDetailState = roomDetailState!!.copy(
                                    destination = selectedName,
                                    destX = latLng.longitude, // ★ 저장
                                    destY = latLng.latitude   // ★ 저장
                                )
                            } else {
                                // [비로그인] 목적지 좌표 저장
                                onNonLoggedInStateChange(
                                    nonLoggedInState.copy(
                                        destination = selectedName,
                                        destX = latLng.longitude, // ★ 저장
                                        destY = latLng.latitude   // ★ 저장
                                    )
                                )
                            }
                        }
                        "startPoint" -> {
                            if (roomId != null && roomDetailState != null && loggedInState.currentUser != null) {
                                // [로그인] 내 출발지 좌표 저장
                                val updatedMembers = roomDetailState!!.members.map { member ->
                                    if (member.user.id == loggedInState.currentUser!!.id) {
                                        member.copy(
                                            startPoint = selectedName,
                                            x = latLng.longitude, // ★ 저장
                                            y = latLng.latitude,  // ★ 저장
                                            isReady = false
                                        )
                                    } else member
                                }
                                roomDetailState = roomDetailState!!.copy(members = updatedMembers)
                            } else {
                                // [비로그인] 멤버 출발지 좌표 저장
                                if (memberIndex != -1) {
                                    val updatedMembers = nonLoggedInState.members.toMutableList()
                                    val oldMember = updatedMembers[memberIndex]
                                    updatedMembers[memberIndex] = oldMember.copy(
                                        startPoint = selectedName,
                                        x = latLng.longitude, // ★ 저장
                                        y = latLng.latitude   // ★ 저장
                                    )
                                    onNonLoggedInStateChange(nonLoggedInState.copy(members = updatedMembers))
                                }
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