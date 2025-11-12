package com.miujong.gachiga10

import android.app.AlertDialog
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.ShapeLayer
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.miujong.gachiga10.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.create
import kotlin.math.*

// ───────── 활동 개요 ─────────
// 다수 사용자(출발지 N명) → 공통 목적지까지 자동차 경로 요청/표시
// - Kakao Map v2 렌더링
// - Kakao Mobility Directions(자동차) REST 사용
// - 우선순위(추천/시간/거리/가격) 선택 표시
// - 경로 공유구간(겹침) 탐지 → 빨간선(더블 스트로크)로 강조
// - 화면 회전 시 상태 복원(사용자/목적지/ETA)

class MainActivity : AppCompatActivity() {

    // ───────── 지도/레이어 ─────────
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private var routeLayer: ShapeLayer? = null      // 사용자별 경로
    private var overlapLayer: ShapeLayer? = null    // 공유구간(빨강)

    // ───────── UI ─────────
    private lateinit var btnDestination: Button
    private lateinit var btnAddUser: Button
    private lateinit var btnFindRoute: Button
    private lateinit var txtEta: TextView
    private lateinit var userContainer: LinearLayout
    private lateinit var spinnerPriority: Spinner

    // ───────── 목적지(경도/위도) ─────────
    private var destX: Double? = null
    private var destY: Double? = null

    // ───────── 우선순위 ─────────
    private enum class Priority { RECOMMEND, TIME, DISTANCE, PRICE }
    private var currentPriority: Priority = Priority.RECOMMEND

    // ───────── 사용자 모델 ─────────
    @Parcelize
    data class User(
        val id: Int,
        var name: String,
        var x: Double? = null,     // 경도
        var y: Double? = null,     // 위도
        var placeName: String = "",
        val color: Int
    ) : Parcelable

    private val users = mutableListOf<User>()
    private var nextUserId = 1

    // ───────── 그려진 선 관리 ─────────
    private val currentPolylines = mutableListOf<Polyline>()
    private val overlapPolylines = mutableListOf<Polyline>()

    // ───────── 사용자 색상(빨강 제외) ─────────
    private val userColors = listOf(
        0xFF1976D2.toInt(), // 파랑
        0xFFFF9800.toInt(), // 주황
        0xFF388E3C.toInt(), // 녹색
        0xFF7B1FA2.toInt(), // 보라
        0xFF0097A7.toInt(), // 청록
        0xFF5D4037.toInt(), // 브라운
        0xFF00796B.toInt(), // 딥청록
        0xFFFFC107.toInt()  // 앰버
    )

    // ───────── Retrofit(모빌리티) ─────────
    private val mobilityApi by lazy {
        val auth = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
                .build()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

        MobilityRetrofit.create().newBuilder()
            .client(client)
            .build()
            .create<MobilityApiService>()
    }

    // ───────── Retrofit(로컬 검색) ─────────
    private val localApi by lazy {
        val auth = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
                .build()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

        LocalRetrofit.create().newBuilder()
            .client(client)
            .build()
            .create<LocalApiService>()
    }

    // ───────── 생명주기 ─────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView         = findViewById(R.id.map_view)
        btnDestination  = findViewById(R.id.btn_destination)
        btnAddUser      = findViewById(R.id.btn_add_user)
        btnFindRoute    = findViewById(R.id.btn_find_route)
        txtEta          = findViewById(R.id.txt_eta)
        userContainer   = findViewById(R.id.user_container)
        spinnerPriority = findViewById(R.id.spinner_priority)

        setupPrioritySpinner()

        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) { error.printStackTrace() }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                val sm = map.shapeManager
                routeLayer   = sm?.addLayer(ShapeLayerOptions.from("routes"))   ?: sm?.layer
                overlapLayer = sm?.addLayer(ShapeLayerOptions.from("overlaps")) ?: sm?.layer

                map.moveCamera(
                    CameraUpdateFactory.newCenterPosition(LatLng.from(37.5665, 126.9780), 12)
                )

                // 상태 복원 후 자동 갱신
                if (destX != null && destY != null && users.any { it.x != null && it.y != null }) {
                    drawAllRoutes()
                }
            }
        })

        // 상태 복원
        savedInstanceState?.let { state ->
            state.getParcelableArrayList<User>("users")?.let {
                users.clear()
                users.addAll(it)
                nextUserId = (users.maxOfOrNull { u -> u.id } ?: 0) + 1
                rebuildUserListUI()
            }
            destX = state.getSerializable("destX") as Double?
            destY = state.getSerializable("destY") as Double?
            txtEta.text = state.getString("etaText", "")
        }

        // 최초 사용자 1명
        if (users.isEmpty()) addUser()

        btnDestination.setOnClickListener {
            showSearchDialog("공통 목적지 검색") { place, x, y ->
                destX = x; destY = y
                btnDestination.text = place
                kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(y, x), 16))
            }
        }
        btnAddUser.setOnClickListener { addUser() }
        btnFindRoute.setOnClickListener { drawAllRoutes() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList("users", ArrayList(users))
        outState.putSerializable("destX", destX)
        outState.putSerializable("destY", destY)
        outState.putString("etaText", txtEta.text?.toString() ?: "")
    }

    // ───────── 우선순위 선택 ─────────
    private fun setupPrioritySpinner() {
        val items = listOf("추천(기본)", "시간", "거리", "가격")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spinnerPriority.adapter = adapter
        spinnerPriority.setSelection(0)
        spinnerPriority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentPriority = when (position) {
                    1 -> Priority.TIME
                    2 -> Priority.DISTANCE
                    3 -> Priority.PRICE
                    else -> Priority.RECOMMEND
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // ───────── 사용자 관리 ─────────
    private fun addUser() {
        val color = userColors[(nextUserId - 1) % userColors.size]
        users.add(User(id = nextUserId, name = "사용자 $nextUserId", color = color))
        nextUserId++
        rebuildUserListUI()
    }

    private fun removeUser(id: Int) {
        users.removeAll { it.id == id }
        if (users.isEmpty()) addUser()
        rebuildUserListUI()
    }

    private fun rebuildUserListUI() {
        userContainer.removeAllViews()
        for (u in users) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(4.dp)
            }

            val colorDot = View(this).apply {
                setBackgroundColor(u.color)
                layoutParams = LinearLayout.LayoutParams(14.dp, 14.dp).apply {
                    rightMargin = 8.dp
                    topMargin = 6.dp
                }
            }

            val btn = Button(this).apply {
                text = if (u.placeName.isBlank()) "${u.name} 출발지 선택" else "${u.name}: ${u.placeName}"
                setOnClickListener {
                    showSearchDialog("${u.name} 출발지 검색") { place, x, y ->
                        u.x = x; u.y = y; u.placeName = place
                        text = "${u.name}: $place"
                        kakaoMap?.moveCamera(
                            CameraUpdateFactory.newCenterPosition(LatLng.from(y, x), 16)
                        )
                    }
                }
            }

            val del = Button(this).apply {
                text = "삭제"
                setOnClickListener { removeUser(u.id) }
            }

            row.addView(colorDot)
            row.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(del)
            userContainer.addView(row)
        }
    }

    // ───────── 경로 그리기 ─────────
    private fun drawAllRoutes() {
        val dx = destX; val dy = destY
        if (dx == null || dy == null) {
            Toast.makeText(this, "목적지를 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val map = kakaoMap ?: run {
            Toast.makeText(this, "지도가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val layer = routeLayer ?: run {
            Toast.makeText(this, "레이어 초기화 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 기존 선/표기 초기화
        currentPolylines.forEach { it.remove() }
        currentPolylines.clear()
        overlapPolylines.forEach { it.remove() }
        overlapPolylines.clear()
        txtEta.text = ""

        lifecycleScope.launch {
            val allRoutes: MutableList<List<LatLng>> = mutableListOf()
            val allPoints = mutableListOf<LatLng>()

            // 사용자별 ‘선택 기준’ 경로 1개만 그린다
            for ((idx, u) in users.withIndex()) {
                val ox = u.x; val oy = u.y
                if (ox == null || oy == null) continue

                val result = fetchBestRoute(ox, oy, dx, dy, currentPriority)
                val points = result.points
                val km     = result.km
                val min    = result.min
                val toll   = result.toll

                if (points.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val mp   = MapPoints.fromLatLng(points)
                        val opts = PolylineOptions.from(mp, 12f, u.color)
                        (routeLayer ?: kakaoMap?.shapeManager?.layer)
                            ?.addPolyline(opts)
                            ?.let { currentPolylines.add(it) }

                        val sb = SpannableStringBuilder().apply {
                            when (currentPriority) {
                                Priority.RECOMMEND -> append("${u.name}: 추천 · %.1fkm / %d분".format(km, min))
                                Priority.TIME      -> append("${u.name}: 시간 · %.1fkm / %d분".format(km, min))
                                Priority.DISTANCE  -> append("${u.name}: 거리 · %.1fkm / %d분".format(km, min))
                                Priority.PRICE     -> append("${u.name}: 가격 · 톨게이트 ${toll}원 · %.1fkm / %d분".format(km, min))
                            }
                        }
                        txtEta.append(if (txtEta.text.isBlank()) sb else "\n$sb")
                    }
                    allRoutes += points
                    allPoints += points
                }

                // 연속 요청 완화
                if (idx < users.lastIndex) delay(120L)
            }

            // 화면 맞춤
            if (allPoints.isNotEmpty()) {
                map.moveCamera(CameraUpdateFactory.fitMapPoints(allPoints.toTypedArray(), 80))
            }

            // 공유구간(빨강) 표시: 첫 교차점부터 연속 구간만
            if (allRoutes.size >= 2) {
                val reds = mutableListOf<List<LatLng>>()
                for (i in 0 until allRoutes.size - 1) {
                    for (j in i + 1 until allRoutes.size) {
                        findSharedSegments(
                            a = allRoutes[i],
                            b = allRoutes[j],
                            tolMeters = 8.0,
                            angleTol = 25.0,
                            minRunPts = 6,
                            maxSkip = 2
                        ).forEach { reds.add(it) }
                    }
                }
                if (reds.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val base = overlapLayer ?: kakaoMap?.shapeManager?.layer
                        for (seg in reds) {
                            if (seg.size >= 2) {
                                // 언더레이(흰색, 굵게)
                                base?.addPolyline(
                                    PolylineOptions.from(
                                        MapPoints.fromLatLng(seg), 16f, 0xFFFFFFFF.toInt()
                                    )
                                )?.let { overlapPolylines.add(it) }
                                // 본선(빨강, 조금 얇게)
                                base?.addPolyline(
                                    PolylineOptions.from(
                                        MapPoints.fromLatLng(seg), 12f, 0xFFE53935.toInt()
                                    )
                                )?.let { overlapPolylines.add(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ───────── 경로 선택(1명 기준) ─────────
    private data class BestRoute(
        val points: List<LatLng>,
        val km: Double,
        val min: Int,
        val toll: Int
    )

    private suspend fun fetchBestRoute(
        ox: Double, oy: Double,
        dx: Double, dy: Double,
        criterion: Priority
    ): BestRoute = withContext(Dispatchers.IO) {
        try {
            when (criterion) {
                Priority.PRICE -> {
                    // 대안 경로 포함 후 톨비 우선
                    val res = mobilityApi.getDirections(
                        origin = "$ox,$oy",
                        destination = "$dx,$dy",
                        priority = "RECOMMEND",
                        summary = false,
                        alternatives = true
                    )
                    val routes = res.routes.orEmpty()
                    if (routes.isEmpty()) return@withContext BestRoute(emptyList(), 0.0, 0, 0)

                    val best = routes.minWith(
                        compareBy<Route>({ it.summary?.fare?.toll ?: Int.MAX_VALUE })
                            .thenBy { it.summary?.duration ?: Int.MAX_VALUE }
                    )

                    val pts = buildPoints(best)
                    val km  = (best.summary?.distance ?: 0) / 1000.0
                    val min = (best.summary?.duration ?: 0) / 60
                    val toll = best.summary?.fare?.toll ?: 0
                    BestRoute(pts, km, min, toll)
                }
                Priority.TIME, Priority.DISTANCE, Priority.RECOMMEND -> {
                    val pri = when (criterion) {
                        Priority.TIME      -> "TIME"
                        Priority.DISTANCE  -> "DISTANCE"
                        else               -> "RECOMMEND"
                    }
                    val res = mobilityApi.getDirections(
                        origin = "$ox,$oy",
                        destination = "$dx,$dy",
                        priority = pri,
                        summary = false,
                        alternatives = false
                    )
                    val route = res.routes?.firstOrNull()
                        ?: return@withContext BestRoute(emptyList(), 0.0, 0, 0)

                    val pts = buildPoints(route)
                    val km  = (route.summary?.distance ?: 0) / 1000.0
                    val min = (route.summary?.duration ?: 0) / 60
                    val toll = route.summary?.fare?.toll ?: 0
                    BestRoute(pts, km, min, toll)
                }
            }
        } catch (he: HttpException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "길찾기 실패(${he.code()}): ${he.message()}", Toast.LENGTH_SHORT).show()
            }
            BestRoute(emptyList(), 0.0, 0, 0)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "길찾기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            BestRoute(emptyList(), 0.0, 0, 0)
        }
    }

    // ───────── 좌표 구성 ─────────
    private fun buildPoints(route: Route): List<LatLng> =
        route.sections.orEmpty()
            .flatMap { it.roads.orEmpty() }
            .flatMap { r ->
                val v = r.vertexes ?: emptyList()
                buildList {
                    var i = 0
                    while (i + 1 < v.size) {
                        val x = v[i]     // 경도
                        val y = v[i + 1] // 위도
                        add(LatLng.from(y, x))
                        i += 2
                    }
                }
            }

    // ───────── 공유 구간 탐지(연속 세그먼트) ─────────
    // tolMeters: 두 경로를 '같은 길'로 볼 허용 거리
    // angleTol: 두 경로 진행 방향의 허용 각도 차(도)
    // minRunPts: 공유로 인정할 최소 연속 포인트 수
    // maxSkip  : B 경로를 따라갈 때 허용할 소폭 스킵
    private fun findSharedSegments(
        a: List<LatLng>,
        b: List<LatLng>,
        tolMeters: Double = 8.0,
        angleTol: Double = 25.0,
        minRunPts: Int = 6,
        maxSkip: Int = 2
    ): List<List<LatLng>> {
        val out = mutableListOf<List<LatLng>>()
        var i = 0
        var lastMatchedJ = -1

        fun near(p: LatLng, q: LatLng) = haversineMeters(p, q) <= tolMeters

        while (i < a.size) {
            // 교차점 근방의 최초 근접 인덱스 탐색
            var foundJ = -1
            val jStart = if (lastMatchedJ >= 0) max(0, lastMatchedJ - 25) else 0
            val jEnd   = min(b.lastIndex, if (lastMatchedJ >= 0) lastMatchedJ + 25 else b.lastIndex)
            var j = jStart
            while (j <= jEnd) {
                if (near(a[i], b[j])) { foundJ = j; break }
                j++
            }
            if (foundJ == -1) { i++; continue }

            // 교차점부터 공행 추적(거리 + 방향)
            val segment = ArrayList<LatLng>()
            var ia = i
            var jb = foundJ
            var prevA = a[ia]
            var prevB = b[jb]
            segment.add(prevA)

            while (ia + 1 < a.size) {
                val nextA = a[ia + 1]

                // B는 1:1 매칭하되 소폭 스킵 허용
                var advanced = false
                var bestJ = jb
                var bestD = Double.MAX_VALUE
                val upper = min(b.lastIndex, jb + 1 + maxSkip)
                for (cand in jb + 1..upper) {
                    val d = haversineMeters(nextA, b[cand])
                    if (d < bestD) { bestD = d; bestJ = cand }
                }

                val angA = bearingDeg(prevA, nextA)
                val angB = bearingDeg(prevB, b[bestJ])
                val angDiff = angleDiffDeg(angA, angB)

                if (bestD <= tolMeters && angDiff <= angleTol) {
                    ia += 1
                    jb = bestJ
                    prevA = nextA
                    prevB = b[jb]
                    segment.add(prevA)
                    advanced = true
                }

                if (!advanced) break
            }

            if (segment.size >= minRunPts) {
                out.add(segment)
                lastMatchedJ = jb
                i = ia + 1
            } else {
                i += 1
            }
        }
        return out
    }

    // ───────── 거리/방위 계산 ─────────
    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    private fun bearingDeg(p: LatLng, q: LatLng): Double {
        val lat1 = Math.toRadians(p.latitude)
        val lat2 = Math.toRadians(q.latitude)
        val dLon = Math.toRadians(q.longitude - p.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    private fun angleDiffDeg(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180) 360 - diff else diff
    }

    // ───────── 검색 다이얼로그 ─────────
    private fun showSearchDialog(
        title: String,
        onSelected: (placeName: String, x: Double, y: Double) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search, null)
        val editQuery = dialogView.findViewById<EditText>(R.id.edit_query)
        val listView = dialogView.findViewById<ListView>(R.id.list_results)
        val progress = dialogView.findViewById<ProgressBar>(R.id.progress)

        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("검색", null)
            .setNegativeButton("닫기", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val q = editQuery.text?.toString()?.trim().orEmpty()
                if (q.isEmpty()) return@setOnClickListener

                lifecycleScope.launch {
                    try {
                        progress.visibility = View.VISIBLE
                        val resp = withContext(Dispatchers.IO) { localApi.searchByKeyword(q) }
                        progress.visibility = View.GONE

                        val rows = resp.documents.map {
                            val addr = if (it.roadAddressName.isNotBlank()) it.roadAddressName else it.addressName
                            "${it.placeName} (${addr})"
                        }
                        adapter.clear(); adapter.addAll(rows); adapter.notifyDataSetChanged()

                        listView.setOnItemClickListener { _, _, pos, _ ->
                            val p = resp.documents[pos]
                            onSelected(p.placeName, p.x.toDouble(), p.y.toDouble())
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        progress.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "검색 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    // ───────── 확장/유틸 ─────────
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause()  { mapView.pause(); super.onPause() }
    override fun onDestroy(){ mapView.finish(); super.onDestroy() }
}
