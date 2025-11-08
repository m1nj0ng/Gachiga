package com.example.gachiga

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gachiga.network.Place
import com.example.gachiga.network.RetrofitInstance
import com.example.gachiga.ui.theme.GachigaTheme
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import kotlinx.coroutines.launch
import java.util.Calendar

// --- 데이터 클래스 정의 ---
data class GachigaState(
    val members: List<Member> = listOf(Member(name = "멤버 1")),
    val destination: String = "미설정",
    val arrivalTime: String = "14:00"
)

data class Member(
    val name: String,
    val startPoint: String = "미설정",
    val transport: String = "대중교통"
)

// --- 앱의 화면 경로를 정의하는 객체 ---
object AppDestinations {
    const val INPUT_SCREEN = "input"
    const val MAP_SELECTION_SCREEN = "map_selection"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GachigaTheme {
                GachigaApp()
            }
        }
    }
}

// --- 앱 전체의 내비게이션을 관리하는 최상위 Composable ---
@Composable
fun GachigaApp() {
    val navController = rememberNavController()
    var gachigaState by remember { mutableStateOf(GachigaState()) }

    NavHost(navController = navController, startDestination = AppDestinations.INPUT_SCREEN) {
        // 1. 정보 입력 화면
        composable(AppDestinations.INPUT_SCREEN) {
            InputScreen(
                navController = navController,
                gachigaState = gachigaState,
                onStateChange = { gachigaState = it }
            )
        }
        // 2. 지도에서 위치를 선택하는 화면
        composable("${AppDestinations.MAP_SELECTION_SCREEN}/{type}/{memberIndex}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val memberIndex = backStackEntry.arguments?.getString("memberIndex")?.toInt() ?: -1

            MapSelectionScreen(
                onLocationSelected = { selectedName, _ ->
                    when (type) {
                        "destination" -> {
                            gachigaState = gachigaState.copy(destination = selectedName)
                        }
                        "startPoint" -> {
                            if (memberIndex != -1) {
                                val updatedMembers = gachigaState.members.toMutableList()
                                updatedMembers[memberIndex] = updatedMembers[memberIndex].copy(startPoint = selectedName)
                                gachigaState = gachigaState.copy(members = updatedMembers)
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

// --- 1. 정보 입력 화면 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(
    navController: NavController,
    gachigaState: GachigaState,
    onStateChange: (GachigaState) -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("중간지점 찾기", fontWeight = FontWeight.Bold) }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CommonInfoSection(
                navController = navController,
                state = gachigaState,
                onStateChange = onStateChange
            )
            Divider()
            MemberInfoSection(
                navController = navController,
                members = gachigaState.members,
                onStateChange = { updatedMembers ->
                    onStateChange(gachigaState.copy(members = updatedMembers))
                }
            )
            Button(
                onClick = { /* TODO: 3단계 로직 */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("중간지점 찾기!", fontSize = 18.sp)
            }
        }
    }
}

// --- 공통 정보 섹션 (목적지, 도착시간) ---
@Composable
fun CommonInfoSection(
    navController: NavController,
    state: GachigaState,
    onStateChange: (GachigaState) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("공통 정보", style = MaterialTheme.typography.titleLarge)
        InfoRow(icon = Icons.Default.Flag, title = "목적지") {
            Button(onClick = {
                navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/destination/-1")
            }) {
                Text(state.destination)
            }
        }
        InfoRow(icon = Icons.Default.Schedule, title = "도착 시간") {
            Button(onClick = { showTimePicker = true }) {
                Text(state.arrivalTime)
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            context = LocalContext.current,
            onTimeSelected = { hour, minute ->
                val formattedTime = String.format("%02d:%02d", hour, minute)
                onStateChange(state.copy(arrivalTime = formattedTime))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

// --- 멤버별 정보 섹션 ---
@Composable
fun MemberInfoSection(
    navController: NavController,
    members: List<Member>,
    onStateChange: (List<Member>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("멤버별 정보", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                val newMember = Member(name = "멤버 ${members.size + 1}")
                onStateChange(members + newMember)
            }) {
                Icon(Icons.Default.Add, contentDescription = "멤버 추가")
                Text("멤버 추가")
            }
        }
        members.forEachIndexed { index, member ->
            MemberCard(
                navController = navController,
                member = member,
                memberIndex = index,
                onMemberChange = { updatedMember ->
                    val updatedList = members.toMutableList()
                    updatedList[index] = updatedMember
                    onStateChange(updatedList)
                },
                onRemoveMember = {
                    if (members.size > 1) {
                        onStateChange(members.filter { it != member })
                    }
                }
            )
        }
    }
}

// --- 각 멤버의 정보 카드 ---
@Composable
fun MemberCard(
    navController: NavController,
    member: Member,
    memberIndex: Int,
    onMemberChange: (Member) -> Unit,
    onRemoveMember: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRemoveMember) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "멤버 삭제")
                }
            }
            InfoRow(icon = Icons.Default.MyLocation, title = "출발지") {
                Button(onClick = {
                    navController.navigate("${AppDestinations.MAP_SELECTION_SCREEN}/startPoint/$memberIndex")
                }) {
                    Text(member.startPoint)
                }
            }
            InfoRow(icon = Icons.Default.DirectionsCar, title = "교통수단") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransportButton(text = "대중교통", isSelected = member.transport == "대중교통") {
                        onMemberChange(member.copy(transport = "대중교통"))
                    }
                    TransportButton(text = "자차", isSelected = member.transport == "자차") {
                        onMemberChange(member.copy(transport = "자차"))
                    }
                }
            }
        }
    }
}

// --- 2. 검색 및 지도 선택을 위한 새로운 화면 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSelectionScreen(
    onLocationSelected: (String, LatLng) -> Unit,
    onCancel: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ★★★★★ 본인의 REST API 키로 반드시 교체해야 합니다! ★★★★★
    val KAKAO_API_KEY = "KakaoAK d162317df274417a97079a2d645e51a7" // "d162..." 부분을 실제 키로 바꾸세요.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("위치 검색 및 선택") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // 1. 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                label = { Text("장소, 주소 검색") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            coroutineScope.launch {
                                try {
                                    val response = RetrofitInstance.api.searchByKeyword(KAKAO_API_KEY, searchQuery)
                                    searchResults = response.documents
                                } catch (e: Exception) {
                                    Log.e("MapSelectionScreen", "API Error: ${e.message}")
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "검색")
                    }
                }
            )

            // 2. 지도와 검색 결과 영역을 담는 Box
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        MapView(context).also { mapView ->
                            mapView.start(object : MapLifeCycleCallback() {
                                override fun onMapDestroy() {}
                                override fun onMapError(error: Exception) {}
                            }, object : KakaoMapReadyCallback() {
                                override fun onMapReady(map: KakaoMap) {
                                    kakaoMap = map
                                    map.setOnMapClickListener { _, _, _, _ ->
                                        searchResults = emptyList()
                                    }
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 3. 검색 결과 목록
                if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.7f)
                            .background(Color.White)
                    ) {
                        items(searchResults) { place ->
                            ListItem(
                                headlineContent = { Text(place.placeName, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(place.roadAddressName.ifEmpty { place.addressName }) },
                                modifier = Modifier.clickable {
                                    selectedPlace = place
                                    val position = LatLng.from(place.latitude.toDouble(), place.longitude.toDouble())
                                    kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(position, 16))
                                    searchResults = emptyList()
                                }
                            )
                            Divider()
                        }
                    }
                }

                // 4. 최종 선택 버튼 (장소가 선택되었을 때만 보임)
                selectedPlace?.let { place ->
                    Button(
                        onClick = {
                            onLocationSelected(
                                place.placeName,
                                LatLng.from(place.latitude.toDouble(), place.longitude.toDouble())
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text("'${place.placeName}'으로 설정")
                    }
                }
            }
        }
    }
}

// --- 나머지 재사용 가능한 Composable 함수들 ---

@Composable
fun InfoRow(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = title)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        content()
    }
}

@Composable
fun TransportButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder
    ) {
        Text(text)
    }
}

@Composable
fun TimePickerDialog(
    context: Context,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hour, minute -> onTimeSelected(hour, minute) },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24시간 형식
        ).apply { setOnDismissListener { onDismiss() } }
    }
    DisposableEffect(Unit) {
        timePickerDialog.show()
        onDispose { timePickerDialog.dismiss() }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GachigaTheme {
        InputScreen(
            navController = rememberNavController(),
            gachigaState = GachigaState(),
            onStateChange = {}
        )
    }
}
