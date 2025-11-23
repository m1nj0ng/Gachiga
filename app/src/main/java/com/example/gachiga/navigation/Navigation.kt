package com.example.gachiga.navigation

import android.util.Log
import androidx.compose.animation.core.copy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.gachiga.ui.result.ResultScreen
import com.example.gachiga.ui.result.VoteScreen
import com.example.gachiga.ui.room.RoomDetailScreen
import com.kakao.sdk.user.UserApiClient

// 로그인 관련 경로 제거
object AppDestinations {
    const val START_SCREEN = "start"
    const val LOBBY_SCREEN = "lobby"
    const val INPUT_SCREEN = "input"
    const val MAP_SELECTION_SCREEN = "map_selection"
    const val CREATE_ROOM_SCREEN = "create_room"
    const val ROOM_DETAIL_SCREEN = "room_detail/{roomId}"
    const val RESULT_SCREEN = "result"
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

    NavHost(navController = navController, startDestination = AppDestinations.START_SCREEN) {

        composable(AppDestinations.START_SCREEN) {
            StartScreen(
                onNavigateToLogin = {
                    // "로그인" 버튼 클릭 시, 로비 화면으로 이동
                    UserApiClient.instance.me { user, error ->
                        if (error != null) {
                            Log.e("KAKAO_USER_INFO", "사용자 정보 요청 실패", error)
                        } else if (user != null) {
                            // 로그인 상태 업데이트
                            onLoggedInStateChange(
                                loggedInState.copy(
                                    currentUser = User(
                                        id = user.id.toString(),
                                        nickname = user.kakaoAccount?.profile?.nickname ?: "사용자",
                                        profileImageUrl = user.kakaoAccount?.profile?.thumbnailImageUrl
                                            ?: ""
                                    )
                                )
                            )
                            // 로비 화면으로 이동
                            navController.navigate(AppDestinations.LOBBY_SCREEN) {
                                popUpTo(AppDestinations.START_SCREEN) { inclusive = true }
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
                    loggedInState.currentUser?.let { user ->
                        val hostMember = RoomMember(user = user, isHost = true)
                        roomDetailState = newRoom.copy(members = listOf(hostMember))
                        navController.navigate("room_detail/${newRoom.roomId}")
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
            if (roomDetailState != null && roomDetailState!!.roomId == roomId) {
                // 추천 경로가 있는지에 따라 다른 화면을 보여줌
                if (roomDetailState!!.suggestedRoutes.isEmpty()) {
                    // 추천 경로가 없을 때: 기존의 방 상세 정보 화면
                    RoomDetailScreen(
                        navController = navController,
                        loggedInUser = loggedInState.currentUser!!,
                        roomDetail = roomDetailState!!,
                        onStateChange = { roomDetailState = it },
                        onCalculate = {
                            // 계산 버튼 클릭 시, 가짜 데이터를 생성하여 상태 업데이트
                            val dummyRoutes = listOf(
                                SuggestedRoute("1", "강남역 2호선", "서울 강남구 강남대로 396", "1시간 12분", "3,200원"),
                                SuggestedRoute("2", "사당역 2,4호선", "서울 동작구 동작대로 3", "1시간 25분", "2,800원"),
                                SuggestedRoute("3", "판교역 신분당선", "경기 성남시 분당구 판교역로 160", "1시간 40분", "4,150원")
                            )
                            roomDetailState = roomDetailState?.copy(suggestedRoutes = dummyRoutes)
                        }
                    )
                } else {
                    // 추천 경로가 있을 때: 로그인용 투표 화면 (VoteScreen)
                    val isHost = roomDetailState!!.members.find { it.user.id == loggedInState.currentUser!!.id }?.isHost ?: false
                    VoteScreen(
                        navController = navController,
                        loggedInUser = loggedInState.currentUser!!,
                        members = roomDetailState!!.members, // ★ 멤버 목록 전달
                        routes = roomDetailState!!.suggestedRoutes,
                        isHost = isHost,
                        onVote = { routeId, userId ->
                            // 투표 로직
                            val updatedRoutes = roomDetailState!!.suggestedRoutes.map { route ->
                                if (route.id == routeId) {
                                    val newVoters = if (userId in route.voters) {
                                        route.voters - userId
                                    } else {
                                        (route.voters + userId).distinct() // 중복 투표 방지 (만약 필요하다면)
                                    }
                                    route.copy(voters = newVoters)
                                } else {
                                    route
                                }
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
                            // 최종 선택 로직
                            val finalPlaceName = roomDetailState!!.suggestedRoutes.find { it.id == routeId }?.placeName
                            roomDetailState = roomDetailState!!.copy(finalPlace = finalPlaceName)
                        }
                    )
                }
            }
        }

        // 지도 선택 화면 (비로그인/로그인 공용)
        composable(
            // 1. roomId를 선택적 파라미터로 설정하여 두 가지 경로를 모두 처리
            route = "${AppDestinations.MAP_SELECTION_SCREEN}/{type}/{memberIndex}?roomId={roomId}",
            arguments = listOf(navArgument("roomId") { nullable = true })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val memberIndex = backStackEntry.arguments?.getString("memberIndex")?.toInt() ?: -1
            val roomId = backStackEntry.arguments?.getString("roomId")

            MapSelectionScreen(
                onLocationSelected = { selectedName, _ ->
                    when (type) {
                        "destination" -> {
                            // 2. roomId 유무로 로그인/비로그인 상태를 구분하여 올바른 상태를 업데이트
                            if (roomId != null && roomDetailState != null) {
                                // 로그인 버전: roomDetailState 업데이트
                                roomDetailState = roomDetailState!!.copy(destination = selectedName)
                            } else {
                                // 비로그인 버전: nonLoggedInState 업데이트
                                onNonLoggedInStateChange(nonLoggedInState.copy(destination = selectedName))
                            }
                        }
                        "startPoint" -> {
                            // 3. 출발지 설정도 동일하게 구분하여 처리
                            if (roomId != null && roomDetailState != null && loggedInState.currentUser != null) {
                                // 로그인 버전: 현재 사용자의 출발지 업데이트 및 준비완료 해제
                                val updatedMembers = roomDetailState!!.members.map { member ->
                                    if (member.user.id == loggedInState.currentUser!!.id) {
                                        member.copy(startPoint = selectedName, isReady = false)
                                    } else {
                                        member
                                    }
                                }
                                roomDetailState = roomDetailState!!.copy(members = updatedMembers)
                            } else {
                                // 비로그인 버전: memberIndex를 사용하여 출발지 업데이트
                                if (memberIndex != -1) {
                                    val updatedMembers = nonLoggedInState.members.toMutableList()
                                    updatedMembers[memberIndex] =
                                        updatedMembers[memberIndex].copy(startPoint = selectedName)
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

        // 비로그인용 결과 화면 composable 블록 추가
        composable(AppDestinations.RESULT_SCREEN) {
            // TODO: 실제 계산 결과를 여기에 전달해야 함
            // 지금은 임시로 만든 가짜 데이터를 사용합니다.
            val dummyRoutes = listOf(
                SuggestedRoute("1", "강남역 2호선", "서울 강남구 강남대로 396", "1시간 12분", "3,200원"),
                SuggestedRoute("2", "사당역 2,4호선", "서울 동작구 동작대로 3", "1시간 25분", "2,800원"),
                SuggestedRoute("3", "판교역 신분당선", "경기 성남시 분당구 판교역로 160", "1시간 40분", "4,150원")
            )
            ResultScreen(
                navController = navController,
                routes = dummyRoutes
            )
        }
    }
}