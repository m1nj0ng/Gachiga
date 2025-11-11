package com.example.gachiga

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.example.gachiga.data.GachigaState
import com.example.gachiga.data.LoggedInState
import com.example.gachiga.navigation.GachigaApp
import com.example.gachiga.ui.theme.GachigaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GachigaTheme {
                val navController = rememberNavController()
                var nonLoggedInState by remember { mutableStateOf(GachigaState()) }
                var loggedInState by remember { mutableStateOf(LoggedInState()) } // 로그인 상태 변수

                GachigaApp(
                    navController = navController,
                    nonLoggedInState = nonLoggedInState,
                    loggedInState = loggedInState, // 전달
                    onNonLoggedInStateChange = { nonLoggedInState = it },
                    onLoggedInStateChange = { loggedInState = it } // 전달
                )
            }
        }
    }
}
