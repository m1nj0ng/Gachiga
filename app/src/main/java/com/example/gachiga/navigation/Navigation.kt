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
                        val hostMember = RoomMember(user = loggedInState.currentUser!!, isHost = true)
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
                RoomDetailScreen(
                    navController = navController,
                    loggedInUser = loggedInState.currentUser!!,
                    roomDetail = roomDetailState!!,
                    onStateChange = { roomDetailState = it }
                )
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

            MapSelectionScreen(
                onLocationSelected = { selectedName, _ ->
                    when (type) {
                        "destination" -> {
                            // RoomDetailScreen에서 온 요청인지 확인
                            if (roomId != null && roomDetailState != null) {
                                roomDetailState = roomDetailState!!.copy(destination = selectedName)
                            } else { // 비로그인 버전의 요청
                                onNonLoggedInStateChange(nonLoggedInState.copy(destination = selectedName))
                            }
                        }

                        "startPoint" -> {
                            // ★★★ 출발지 설정 로직 수정 ★★★
                            // 로그인 상태의 방에서 출발지를 설정하는 경우
                            if (roomId != null && roomDetailState != null) {
                                val loggedInUser = loggedInState.currentUser
                                if (loggedInUser != null) {
                                    // 현재 로그인한 사용자의 멤버 정보를 찾아서 출발지를 업데이트
                                    val updatedMembers = roomDetailState!!.members.map { member ->
                                        if (member.user.id == loggedInUser.id) {
                                            member.copy(startPoint = selectedName, isReady = false) // 출발지 변경 시 준비완료 해제
                                        } else {
                                            member
                                        }
                                    }
                                    // 변경된 멤버 목록으로 전체 방 상태를 업데이트
                                    roomDetailState = roomDetailState!!.copy(members = updatedMembers)
                                }
                            } else { // 비로그인 버전의 요청 (기존 로직 유지)
                                val updatedMembers = nonLoggedInState.members.toMutableList()
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