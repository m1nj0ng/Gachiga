package com.example.gachiga.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.gachiga.data.GachigaState
import com.example.gachiga.ui.input.InputScreen
import com.example.gachiga.ui.map.MapSelectionScreen
import com.example.gachiga.ui.start.StartScreen
import com.example.gachiga.data.LoggedInState
import com.example.gachiga.ui.lobby.LobbyScreen

// 로그인 관련 경로 제거
object AppDestinations {
    const val START_SCREEN = "start"
    const val LOBBY_SCREEN = "lobby"
    const val INPUT_SCREEN = "input"
    const val MAP_SELECTION_SCREEN = "map_selection"
    const val CREATE_ROOM_SCREEN = "create_room"
}

@Composable
fun GachigaApp(
    navController: NavHostController,
    nonLoggedInState: GachigaState,
    loggedInState: LoggedInState,
    onNonLoggedInStateChange: (GachigaState) -> Unit,
    onLoggedInStateChange: (LoggedInState) -> Unit
) {
    NavHost(navController = navController, startDestination = AppDestinations.START_SCREEN) {

        // 시작 화면 (로그인 로직 제거)
        composable(AppDestinations.START_SCREEN) {
            StartScreen(
                onNavigateToLogin = {
                    // "로그인" 버튼 클릭 시, 로비 화면으로 이동
                    // TODO: 실제 로그인 로직 성공 후 이 부분을 호출해야 함
                    navController.navigate(AppDestinations.LOBBY_SCREEN) {
                        popUpTo(AppDestinations.START_SCREEN) { inclusive = true }
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
            LobbyScreen(navController = navController, state = loggedInState)
        }

        // 비로그인 시 보일 입력 화면
        composable(AppDestinations.INPUT_SCREEN) {
            InputScreen(
                navController = navController,
                gachigaState = nonLoggedInState,
                onStateChange = onNonLoggedInStateChange
            )
        }

        // 지도 선택 화면
        composable("${AppDestinations.MAP_SELECTION_SCREEN}/{type}/{memberIndex}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val memberIndex = backStackEntry.arguments?.getString("memberIndex")?.toInt() ?: -1

            MapSelectionScreen(
                onLocationSelected = { selectedName, _ ->
                    when (type) {
                        "destination" -> {
                            onNonLoggedInStateChange(nonLoggedInState.copy(destination = selectedName))
                        }
                        "startPoint" -> {
                            if (memberIndex != -1) {
                                val updatedMembers = nonLoggedInState.members.toMutableList()
                                updatedMembers[memberIndex] =
                                    updatedMembers[memberIndex].copy(startPoint = selectedName)
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