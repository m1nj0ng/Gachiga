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
import com.kakao.vectormap.shape.*
import com.miujong.gachiga10.data.model.*
import com.miujong.gachiga10.data.repository.RouteRepository
import com.miujong.gachiga10.network.NetworkModule
import com.miujong.gachiga10.util.RouteMath
import kotlinx.coroutines.*

enum class TravelMode { CAR, TRANSIT, WALK }

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var mapView: MapView
    private lateinit var btnDestination: Button
    private lateinit var btnAddUser: Button
    private lateinit var btnFindRoute: Button
    private lateinit var txtEta: TextView
    private lateinit var userContainer: LinearLayout

    // Map Objects
    private var kakaoMap: KakaoMap? = null
    private var routeLayer: ShapeLayer? = null
    private val currentPolylines = mutableListOf<Polyline>()

    // Data
    private var destX: Double? = null
    private var destY: Double? = null

    data class User(
        val id: Int,
        var name: String,
        var x: Double? = null,
        var y: Double? = null,
        var placeName: String = "",
        val color: Int,
        var mode: TravelMode = TravelMode.CAR,
        var searchOption: Int = 0
    )

    private val users = mutableListOf<User>()
    private var nextUserId = 1
    private val userColors = listOf(
        0xFF1976D2.toInt(), 0xFFFF9800.toInt(), 0xFF388E3C.toInt(), 0xFF7B1FA2.toInt(),
        0xFF0097A7.toInt(), 0xFF5D4037.toInt(), 0xFF00796B.toInt(), 0xFFFFC107.toInt()
    )

    private val repository = RouteRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initMap()
        if (users.isEmpty()) addUser()
    }

    private fun initViews() {
        mapView = findViewById(R.id.map_view)
        btnDestination = findViewById(R.id.btn_destination)
        btnAddUser = findViewById(R.id.btn_add_user)
        btnFindRoute = findViewById(R.id.btn_find_route)
        txtEta = findViewById(R.id.txt_eta)
        userContainer = findViewById(R.id.user_container)

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

    private fun initMap() {
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {}
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                routeLayer = map.shapeManager?.addLayer(ShapeLayerOptions.from("tmap_routes"))
                map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(37.5665, 126.9780), 12))
            }
        })
    }

    private fun addUser() {
        val color = userColors[(nextUserId - 1) % userColors.size]
        users.add(User(nextUserId, "사용자 $nextUserId", color = color))
        nextUserId++
        rebuildUserListUI()
    }

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

            // 1. 색상 점
            row.addView(View(this).apply {
                setBackgroundColor(u.color)
                layoutParams = LinearLayout.LayoutParams(20, 20).apply { setMargins(10, 0, 10, 0) }
            })

            // 2. 이름/장소 버튼
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

            // 3. 옵션 스피너
            val optionSpinner = Spinner(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            }

            // 4. 모드 스피너
            val modeSpinner = Spinner(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
                val modes = listOf("자동차", "대중교통", "도보")
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modes)

                val initialPos = when(u.mode) {
                    TravelMode.CAR -> 0; TravelMode.TRANSIT -> 1; TravelMode.WALK -> 2
                }
                setSelection(initialPos)

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        u.mode = when(position) {
                            0 -> TravelMode.CAR; 1 -> TravelMode.TRANSIT; 2 -> TravelMode.WALK; else -> TravelMode.CAR
                        }

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

            // 5. 옵션 리스너
            optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val selectedText = p0?.getItemAtPosition(pos).toString()
                    u.searchOption = mapOption(u.mode, selectedText)
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            row.addView(optionSpinner)

            // 6. 삭제 버튼
            row.addView(Button(this).apply {
                text = "X"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { users.remove(u); if (users.isEmpty()) addUser(); rebuildUserListUI() }
            })
            userContainer.addView(row)
        }
    }

    private fun mapOption(mode: TravelMode, text: String): Int {
        return when (mode) {
            TravelMode.CAR -> when (text) {
                "추천 경로" -> 0; "최단 거리" -> 10; "최소 시간" -> 2; "무료 우선" -> 1; else -> 0
            }
            TravelMode.TRANSIT -> when (text) {
                "최적 경로" -> 0; "최소 환승" -> 1; "최소 시간" -> 2; "최소 도보" -> 3; else -> 0
            }
            else -> 0
        }
    }

    private fun drawAllRoutes() {
        val dx = destX ?: return
        val dy = destY ?: return

        // 1. 지도 & 텍스트 초기화
        currentPolylines.forEach { it.remove() }
        currentPolylines.clear()
        kakaoMap?.shapeManager?.getLayer("overlaps")?.removeAll()
        txtEta.text = ""

        val allCarRoutes = mutableListOf<List<LatLng>>()
        val allPoints = mutableListOf<LatLng>()

        lifecycleScope.launch {
            for (u in users) {
                val sx = u.x ?: continue
                val sy = u.y ?: continue

                when (u.mode) {
                    TravelMode.CAR -> {
                        val tmap = repository.fetchTmapCarRoute(sx, sy, dx, dy, u.searchOption)
                        if (tmap.points.isNotEmpty()) {
                            drawPolyline(tmap.points, u.color)

                            val optLabel = when(u.searchOption) { 10->"최단"; 2->"시간"; 1->"무료"; else->"추천" }

                            // 요금 포함
                            appendLog("${u.name}: 자동차($optLabel) ${"%.1f".format(tmap.km)}km / ${tmap.minutes}분 / 톨게이트 ${tmap.toll}원")

                            allCarRoutes.add(tmap.points); allPoints.addAll(tmap.points)
                        }
                    }

                    TravelMode.TRANSIT -> {
                        val list = repository.fetchTransitOptions(sx, sy, dx, dy, u.searchOption)
                        val bestRoute = list.firstOrNull()

                        if (bestRoute?.path != null) {
                            val fullPathPoints = mutableListOf<LatLng>()
                            bestRoute.path.forEach { seg ->
                                if (seg.points.isNotEmpty()) {
                                    val mp = MapPoints.fromLatLng(seg.points)
                                    val transitColor = if (seg.mode == "WALK") 0xFFFFFFFF.toInt()
                                    else parseColorSafe(seg.color)

                                    routeLayer?.addPolyline(PolylineOptions.from(mp, 20f, transitColor))?.let { currentPolylines.add(it) }
                                    routeLayer?.addPolyline(PolylineOptions.from(mp, 12f, u.color))?.let { currentPolylines.add(it) }

                                    fullPathPoints.addAll(seg.points)
                                }
                            }
                            if (fullPathPoints.isNotEmpty()) {
                                allCarRoutes.add(fullPathPoints); allPoints.addAll(fullPathPoints)
                            }
                        }

                        if (bestRoute != null) {
                            val optLabel = when(u.searchOption) { 1->"환승"; 2->"시간"; 3->"도보"; else->"최적" }
                            val summary = bestRoute.path?.joinToString(" -> ") { seg ->
                                when (seg.mode) { "WALK"->"도보"; "BUS"->"버스(${seg.name?:""})"; "SUBWAY"->"지하철(${seg.name?:""})"; else->"" }
                            } ?: ""

                            // 요금 포함
                            appendLog("${u.name}: 대중교통($optLabel) ${"%.1f".format(bestRoute.distanceKm)}km / ${bestRoute.minutes}분 / ${bestRoute.fare}원\n      └ $summary")
                        } else {
                            appendLog("${u.name}: 대중교통 경로 없음")
                        }
                    }

                    TravelMode.WALK -> {
                        val walk = repository.fetchTmapWalkRoute(sx, sy, dx, dy)
                        if (walk.points.isNotEmpty()) {
                            drawPolyline(walk.points, u.color)
                            appendLog("${u.name}: 도보 ${"%.1f".format(walk.km)}km / ${walk.minutes}분")
                            allCarRoutes.add(walk.points); allPoints.addAll(walk.points)
                        }
                    }
                }
                delay(100)
            }

            if (allPoints.isNotEmpty()) {
                kakaoMap?.moveCamera(CameraUpdateFactory.fitMapPoints(allPoints.toTypedArray(), 80))
            }

            drawSharedSegmentsAndRecommend(allCarRoutes)
        }
    }

    private suspend fun drawSharedSegmentsAndRecommend(routes: List<List<LatLng>>) {
        if (routes.size < 2) return

        val sm = kakaoMap?.shapeManager ?: return
        val overlapLayer = sm.getLayer("overlaps") ?: sm.addLayer(ShapeLayerOptions.from("overlaps"))

        val sharedSegments = RouteMath.findAllSharedSegments(routes)

        sharedSegments.forEach { seg ->
            overlapLayer.addPolyline(PolylineOptions.from(MapPoints.fromLatLng(seg), 16f, 0xFFFFFFFF.toInt()))
            overlapLayer.addPolyline(PolylineOptions.from(MapPoints.fromLatLng(seg), 12f, 0xFFE53935.toInt()))
        }

        // 합류 지점 텍스트 추천 기능은 유지
        if (sharedSegments.isNotEmpty()) {
            val meetPoint = sharedSegments.first().first()
            val placeName = repository.getBestMeetPlaceName(meetPoint.latitude, meetPoint.longitude)

            val header = "\n✨ 추천 합류 장소: $placeName\n--------------------------------------------\n"
            txtEta.text = header + txtEta.text
        }
    }

    private fun drawPolyline(points: List<LatLng>, color: Int) {
        val opts = PolylineOptions.from(MapPoints.fromLatLng(points), 12f, color)
        routeLayer?.addPolyline(opts)?.let { currentPolylines.add(it) }
    }

    private fun appendLog(text: String) {
        txtEta.append(if (txtEta.text.isBlank()) text else "\n$text")
    }

    private fun parseColorSafe(colorStr: String?): Int {
        return try {
            val c = colorStr ?: "#888888"
            android.graphics.Color.parseColor(if (c.startsWith("#")) c else "#$c")
        } catch (e: Exception) { 0xFF888888.toInt() }
    }

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

    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause() { mapView.pause(); super.onPause() }
    override fun onDestroy() { mapView.finish(); super.onDestroy() }
}