package com.example.gachiga.ui.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.gachiga.data.Member
import com.example.gachiga.data.RouteRepository
import com.example.gachiga.data.SuggestedRoute
import com.example.gachiga.util.RouteLogicManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidpointSelectScreen(
    navController: NavController,
    repository: RouteRepository,
    members: List<Member>,
    onPlaceSelected: (SuggestedRoute) -> Unit
) {
    val logicManager = remember { RouteLogicManager(repository) }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var recommendedPlaces by remember { mutableStateOf<List<SuggestedRoute>>(emptyList()) }

    // 화면 진입 시 자동으로 추천 로직 실행
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // 기존 로직 매니저의 추천 함수 재사용
                recommendedPlaces = logicManager.recommendMidpointPlaces(members)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("추천 장소 선택", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("중간 지점을 분석하고 있습니다...", color = Color.Gray)
                }
            } else {
                if (recommendedPlaces.isEmpty()) {
                    Text(
                        text = "추천할 만한 장소를 찾지 못했습니다.",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "원하는 장소를 선택하세요",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(recommendedPlaces) { place ->
                            SuggestionCard(place = place) {
                                // ★ 클릭 시 선택 콜백 실행
                                onPlaceSelected(place)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 리스트 아이템 카드 디자인 (VoteScreen과 유사하게)
@Composable
fun SuggestionCard(
    place: SuggestedRoute,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = place.placeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // 카테고리 (예: 지하철역, 카페)
                AssistChip(
                    onClick = {},
                    label = { Text(place.totalFee) }, // totalFee 필드를 카테고리명으로 쓰고 있음
                    enabled = false
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = place.address,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
        }
    }
}