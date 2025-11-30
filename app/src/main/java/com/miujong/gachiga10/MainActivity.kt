package com.miujong.gachiga10

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.miujong.gachiga10.data.model.*
import com.miujong.gachiga10.data.repository.RouteRepository
import com.miujong.gachiga10.network.NetworkModule
import com.miujong.gachiga10.util.RouteLogicManager
import com.miujong.gachiga10.util.RouteVisualizer
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * [메인 액티비티 - UI 컨트롤러]
 * - 역할: 사용자의 입력(목적지, 시간, 사용자 추가)을 받고, 결과를 화면에 표시합니다.
 * - 특징: 복잡한 경로 계산 로직은 [RouteLogicManager]로, 지도 그리기 로직은 [RouteVisualizer]로 위임했습니다.
 * 따라서 이 파일은 순수하게 '사용자와의 상호작용'에만 집중합니다.
 */
class MainActivity : AppCompatActivity() {

    // ========================================================================
    // UI Components (XML 뷰 연결)
    // ========================================================================
    private lateinit var mapView: MapView
    private lateinit var btnDestination: Button
    private lateinit var btnTimeSetting: Button
    private lateinit var btnAddUser: Button
    private lateinit var btnFindRoute: Button
    private lateinit var txtEta: TextView
    private lateinit var userContainer: LinearLayout

    // ========================================================================
    // Dependencies (의존성 객체)
    // ========================================================================
    private var kakaoMap: KakaoMap? = null

    /** [화가] 지도 그리기 전담 객체 (선, 마커 등) */
    private var visualizer: RouteVisualizer? = null

    /** [두뇌] 경로 계산 및 로직 처리 전담 객체 */
    private lateinit var logicManager: RouteLogicManager

    // ========================================================================
    // Data State (데이터 상태)
    // ========================================================================
    private var destX: Double? = null // 목적지 경도
    private var destY: Double? = null // 목적지 위도
    private var targetArrivalTime: Calendar? = null // 목표 도착 시간

    private val users = mutableListOf<User>() // 사용자 리스트
    private var nextUserId = 1
    private val userColors = listOf(
        0xFF1976D2.toInt(), 0xFFFF9800.toInt(), 0xFF388E3C.toInt(), 0xFF7B1FA2.toInt(),
        0xFF0097A7.toInt(), 0xFF5D4037.toInt(), 0xFF00796B.toInt(), 0xFFFFC107.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 로직 매니저 초기화 (Repository 주입)
        logicManager = RouteLogicManager(RouteRepository())

        initViews()
        initMap()
        // 앱 시작 시 기본 사용자 1명 추가
        if (users.isEmpty()) addUser()
    }

    /**
     * UI 초기화 및 이벤트 리스너 등록
     */
    private fun initViews() {
        mapView = findViewById(R.id.map_view)
        btnDestination = findViewById(R.id.btn_destination)
        btnTimeSetting = findViewById(R.id.btn_time_setting)
        btnAddUser = findViewById(R.id.btn_add_user)
        btnFindRoute = findViewById(R.id.btn_find_route)
        txtEta = findViewById(R.id.txt_eta)
        userContainer = findViewById(R.id.user_container)

        // 1. 공통 목적지 검색
        btnDestination.setOnClickListener {
            showSearchDialog("공통 목적지 검색") { place, x, y ->
                destX = x; destY = y
                btnDestination.text = place
                // 목적지로 지도 이동
                kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(y, x), 16))
            }
        }

        // 2. 도착 시간 설정 (TimePicker)
        btnTimeSetting.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(this, { _, hour, minute ->
                val selectedTime = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                }
                // 과거 시간을 선택하면 내일로 간주
                if (selectedTime.before(now)) selectedTime.add(java.util.Calendar.DAY_OF_MONTH, 1)

                targetArrivalTime = selectedTime
                btnTimeSetting.text = String.format("%02d:%02d 도착", hour, minute)
            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), false).show()
        }

        // 3. 사용자 추가
        btnAddUser.setOnClickListener { addUser() }

        // 4. [핵심] 길찾기 실행 (로직 위임)
        btnFindRoute.setOnClickListener {
            performRouteCalculation()
        }
    }

    /**
     * KakaoMap 초기화
     */
    private fun initMap() {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {}
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                // 지도 준비 완료 시 화가(Visualizer) 고용
                visualizer = RouteVisualizer(map)
                // 초기 위치: 서울 시청 부근
                map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(37.5665, 126.9780), 12))
            }
        })
    }

    /**
     * [경로 계산 실행]
     * 복잡한 계산은 LogicManager에게 넘기고, 결과(String)만 받아서 화면에 표시합니다.
     */
    private fun performRouteCalculation() {
        val dx = destX ?: return
        val dy = destY ?: return
        val painter = visualizer ?: return

        txtEta.text = "경로 계산 중..."

        lifecycleScope.launch {
            // 1. LogicManager에게 작업 지시 (비동기)
            // (API 호출 -> 그룹핑 -> 시간계산 -> 그리기까지 전부 처리함)
            val resultLog = logicManager.calculateRoutes(users, dx, dy, targetArrivalTime, painter)

            // 2. 결과 텍스트 업데이트
            txtEta.text = resultLog
        }
    }

    // ========================================================================
    // UI Helper Functions (사용자 리스트 관리 등)
    // ========================================================================

    /** 사용자 추가 */
    private fun addUser() {
        val color = userColors[(nextUserId - 1) % userColors.size]
        users.add(User(nextUserId, "사용자 $nextUserId", color = color))
        nextUserId++
        rebuildUserListUI()
    }

    /**
     * 사용자 목록 UI를 동적으로 생성 (스크롤 뷰 내부)
     * - 각 행마다 출발지 버튼, 모드 선택(Spinner), 옵션 선택(Spinner), 삭제 버튼 포함
     */
    private fun rebuildUserListUI() {
        userContainer.removeAllViews()
        val carItems = listOf("추천 경로", "최단 거리", "최소 시간", "무료 우선")
        val transitItems = listOf("최적 경로", "최소 환승", "최소 시간", "최소 도보")
        val walkItems = listOf("옵션 없음")

        users.forEach { u ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
                gravity = android.view.Gravity.CENTER_VERTICAL
                weightSum = 10f
            }
            // 1. 색상 표시
            row.addView(View(this).apply {
                setBackgroundColor(u.color)
                layoutParams = LinearLayout.LayoutParams(20, 20).apply { setMargins(10, 0, 10, 0) }
            })
            // 2. 출발지 버튼
            row.addView(Button(this).apply {
                val placeText = if (u.placeName.isBlank()) "출발지" else u.placeName
                text = "${u.name}\n$placeText"
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3.5f)
                setOnClickListener {
                    showSearchDialog("${u.name} 출발지 검색") { place, x, y ->
                        u.x = x; u.y = y; u.placeName = place
                        text = "${u.name}\n$place"
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(y, x), 16))
                    }
                }
            })
            // 3. 옵션/모드 스피너 (상호작용)
            val optionSpinner = Spinner(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            }
            val modeSpinner = Spinner(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
                val modes = listOf("자동차", "대중교통", "도보")
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modes)
                setSelection(u.mode.ordinal)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        u.mode = TravelMode.values()[position]
                        val targetItems = when(u.mode) {
                            TravelMode.CAR -> carItems; TravelMode.TRANSIT -> transitItems; TravelMode.WALK -> walkItems
                        }
                        optionSpinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, targetItems)
                        optionSpinner.isEnabled = (u.mode != TravelMode.WALK)
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
            row.addView(modeSpinner)
            optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val selectedText = p0?.getItemAtPosition(pos).toString()
                    u.searchOption = mapOption(u.mode, selectedText)
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            row.addView(optionSpinner)
            // 4. 삭제 버튼
            row.addView(Button(this).apply {
                text = "X"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { users.remove(u); if (users.isEmpty()) addUser(); rebuildUserListUI() }
            })
            userContainer.addView(row)
        }
    }

    // 옵션 텍스트 -> API 코드 변환
    private fun mapOption(mode: TravelMode, text: String): Int {
        return when (mode) {
            TravelMode.CAR -> when (text) { "추천 경로"->0; "최단 거리"->10; "최소 시간"->2; "무료 우선"->1; else->0 }
            TravelMode.TRANSIT -> when (text) { "최적 경로"->0; "최소 환승"->1; "최소 시간"->2; "최소 도보"->3; else->0 }
            else -> 0
        }
    }

    // 장소 검색 다이얼로그 표시
    private fun showSearchDialog(title: String, onSelected: (String, Double, Double) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_search, null)
        val list = view.findViewById<ListView>(R.id.list_results)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        list.adapter = adapter
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(view).setPositiveButton("검색") { _, _ -> }.setNegativeButton("닫기", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val q = view.findViewById<EditText>(R.id.edit_query).text.toString()
                if (q.isNotBlank()) {
                    lifecycleScope.launch {
                        try {
                            val res = withContext(Dispatchers.IO) { NetworkModule.getLocalApi().searchByKeyword(q) }
                            adapter.clear(); adapter.addAll(res.documents.map { "${it.placeName} (${it.addressName})" })
                            list.setOnItemClickListener { _, _, pos, _ -> val d = res.documents[pos]; onSelected(d.placeName, d.x.toDouble(), d.y.toDouble()); dialog.dismiss() }
                        } catch (e: Exception) { Toast.makeText(this@MainActivity, "에러", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
        dialog.show()
    }

    // ========================================================================
    // [Lifecycle Methods] 지도 리소스 관리
    // - MapView는 메모리와 배터리를 많이 소모하므로, 앱의 생명주기에 맞춰
    //   반드시 pause, resume, finish를 호출해줘야 합니다.
    // ========================================================================

    /**
     * [화면 복귀]
     * 사용자가 앱으로 다시 돌아왔을 때, 멈췄던 지도 렌더링을 재개합니다.
     */
    override fun onResume() { super.onResume(); mapView.resume() }

    /**
     * [화면 이탈]
     * 사용자가 홈 버튼을 누르거나 다른 앱으로 전환했을 때,
     * 불필요한 지도 연산과 GPS 사용을 멈춰 배터리를 절약합니다.
     */
    override fun onPause() { mapView.pause(); super.onPause() }

    /**
     * [앱 종료]
     * 액티비티가 파괴될 때, 지도 엔진이 점유했던 메모리와 스레드를
     * 완전히 해제하여 메모리 누수(Memory Leak)를 방지합니다.
     */
    override fun onDestroy() { mapView.finish(); super.onDestroy() }
}