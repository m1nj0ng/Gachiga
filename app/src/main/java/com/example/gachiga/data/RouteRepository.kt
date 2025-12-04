package com.example.gachiga.data

import com.kakao.vectormap.LatLng
import com.example.gachiga.data.*
import com.example.gachiga.network.*
import com.example.gachiga.util.RouteMath
import com.example.gachiga.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [경로 데이터 저장소 (Repository)]
 * - 역할: API 호출을 수행하고, 응답받은 복잡한 JSON 데이터를 앱 내부 모델(TmapCarRoute, TransitOption 등)로 변환하여 제공합니다.
 * - 주요 기능: 자동차/대중교통/도보 경로 탐색, 합류 지점 지명(Place Name) 검색
 */
class RouteRepository {

    // NetworkModule을 통해 생성된 API 인스턴스들
    private val tmapRouteApi = NetworkModule.getTmapRouteApi()
    private val transitApi = NetworkModule.getTransitApi()
    private val walkApi = NetworkModule.getTmapWalkApi()
    private val localApi = NetworkModule.getLocalApi()

    /**
     * [자동차 경로 탐색]
     * TMAP 자동차 경로 API를 호출하고, GeoJSON 데이터를 파싱하여 좌표 리스트와 요약 정보를 반환합니다.
     * @param option 검색 옵션 (0:추천, 1:무료, 2:최단거리 등)
     */
    suspend fun fetchTmapCarRoute(sx: Double, sy: Double, dx: Double, dy: Double, option: Int): TmapCarRoute = withContext(Dispatchers.IO) {
        try {
            val resp = tmapRouteApi.getCarRoute(
                startX = sx, startY = sy, endX = dx, endY = dy, searchOption = option
            )

            // GeoJSON Feature 중 LineString(선) 데이터만 추출하여 좌표 리스트로 변환
            val lineStrings = resp.features?.filter {
                it.geometry is TmapGeometry.LineString || it.geometry is TmapGeometry.MultiLineString
            } ?: return@withContext TmapCarRoute(emptyList(), 0.0, 0, 0)

            val points = lineStrings.flatMap { feature ->
                when (val geom = feature.geometry) {
                    is TmapGeometry.LineString -> geom.coords.map { LatLng.from(it.lat, it.lon) }
                    is TmapGeometry.MultiLineString -> geom.multiple.flatten().map { LatLng.from(it.lat, it.lon) }
                    else -> emptyList()
                }
            }

            // 첫 번째 Feature의 Properties에서 총 거리/시간/요금 정보 추출
            val props = resp.features.firstOrNull()?.properties
            TmapCarRoute(points, (props?.totalDistance ?: 0) / 1000.0, (props?.totalTime ?: 0) / 60, props?.totalFare ?: 0)
        } catch (e: Exception) {
            // 에러 발생 시 빈 객체 반환 (앱 죽음 방지)
            TmapCarRoute(emptyList(), 0.0, 0, 0)
        }
    }

    /**
     * [대중교통 경로 탐색]
     * 버스/지하철/도보 복합 경로를 탐색하고, 각 구간(Leg)별 상세 정보를 파싱합니다.
     * 특히, 단순 좌표뿐만 아니라 '정류장 리스트'를 추출하여 합류 로직에 활용할 수 있게 합니다.
     */
    suspend fun fetchTransitOptions(sx: Double, sy: Double, dx: Double, dy: Double, option: Int): List<TransitOption> = withContext(Dispatchers.IO) {
        try {
            val body = TransitRouteRequest(
                startX = sx.toString(), startY = sy.toString(),
                endX = dx.toString(), endY = dy.toString(),
                count = 10, format = "json"
            )
            val res = transitApi.getTransitRoutes(body)
            var rawItineraries = res.metaData?.plan?.itineraries.orEmpty()

            // 검색 옵션에 따른 정렬 로직 (1:최소환승, 2:최소시간, 3:최소도보)
            if (rawItineraries.isNotEmpty()) {
                rawItineraries = when (option) {
                    1 -> rawItineraries.sortedBy { it.legs?.count { leg -> leg.mode == "BUS" || leg.mode == "SUBWAY" } ?: 99 }
                    2 -> rawItineraries.sortedBy { it.totalTime ?: 99999 }
                    3 -> rawItineraries.sortedBy {
                        it.legs?.filter { leg -> leg.mode == "WALK" }?.sumOf { leg -> leg.distance ?: 0 } ?: 99999
                    }
                    else -> rawItineraries
                }
            }

            // 검색된 경로들을 TransitOption 객체로 변환
            rawItineraries.mapIndexed { idx, it ->
                // 경로 타이틀 생성 (예: "버스+지하철")
                val modes = it.legs.orEmpty().mapNotNull { leg -> leg.mode }.filter { m -> m != "WALK" }.toSet()
                val title = when {
                    modes.isEmpty() -> "도보"
                    modes.size == 1 -> modes.first()
                    else -> "복합"
                }

                // [상세 경로 파싱] 첫 번째 추천 경로(idx==0)만 상세 좌표를 파싱하여 지도에 그릴 준비를 합니다.
                // (모든 경로를 다 파싱하면 성능 저하 우려)
                val pathSegments = if (idx == 0) {
                    var lastPoint: LatLng? = null
                    val legs = it.legs.orEmpty()

                    legs.mapIndexed { legIdx, leg ->
                        var parsedPoints = RouteMath.parsePassShape(leg.passShape)
                        var segmentDist = leg.distance ?: 0
                        var segmentTime = leg.sectionTime ?: 0

                        // [도보 경로 보정]
                        // TMAP 대중교통 API는 도보 구간의 상세 좌표를 주지 않는 경우가 있어,
                        // 별도의 보행자 API(fetchTmapWalkRoute)를 호출하여 좌표를 채워 넣습니다.
                        if (parsedPoints.isEmpty() && lastPoint != null
                            && legIdx == legs.lastIndex
                            && (leg.mode == "WALK" || leg.mode == null)) {
                            try {
                                val walkResp = walkApi.getWalkRoute(
                                    startX = lastPoint!!.longitude, startY = lastPoint!!.latitude,
                                    endX = dx, endY = dy,
                                    startName = "DropOff", endName = "Destination"
                                )
                                // 보행자 API 결과 파싱 및 대체
                                val walkFeatures = walkResp.features.orEmpty()
                                val walkProps = walkFeatures.firstOrNull()?.properties
                                if (walkProps != null) {
                                    segmentDist = walkProps.totalDistance ?: 0
                                    segmentTime = walkProps.totalTime ?: 0
                                }
                                val walkPoints = walkFeatures
                                    .filter { f -> f.geometry is TmapGeometry.LineString }
                                    .flatMap { f -> (f.geometry as TmapGeometry.LineString).coords.map { c -> LatLng.from(c.lat, c.lon) } }
                                if (walkPoints.isNotEmpty()) parsedPoints = walkPoints
                            } catch (e: Exception) {
                                // 보행자 API 실패 시 직선으로 연결
                                parsedPoints = listOf(lastPoint!!, LatLng.from(dy, dx))
                            }
                        }

                        // 좌표 연결성 보장
                        if (parsedPoints.isEmpty() && lastPoint != null && leg.mode == "WALK") {
                            parsedPoints = listOf(lastPoint!!)
                        }
                        if (parsedPoints.isNotEmpty()) lastPoint = parsedPoints.last()

                        // [정류장 정보 수집] 합류 로직(RouteOptimizer)에서 사용할 정류장 리스트 생성
                        val collectedStations = mutableListOf<StationPoint>()
                        leg.passStopList?.stationList?.forEach { station ->
                            val sName = station.stationName
                            val sLat = station.lat?.toDoubleOrNull() ?: 0.0
                            val sLon = station.lon?.toDoubleOrNull() ?: 0.0
                            if (!sName.isNullOrBlank() && sLat != 0.0 && sLon != 0.0) {
                                collectedStations.add(StationPoint(sName, sLat, sLon))
                            }
                        }

                        TransitPathSegment(
                            points = parsedPoints,
                            mode = leg.mode ?: "WALK",
                            color = leg.routeColor,
                            name = leg.route,
                            stations = collectedStations, // ★ 핵심: 경유 정류장 포함
                            distanceMeters = segmentDist,
                            sectionTimeSeconds = segmentTime,
                            totalFare = 0 // 개별 구간 요금 정보 부재로 0 처리
                        )
                    }
                } else null

                TransitOption(
                    title = "$title 경로",
                    minutes = (it.totalTime ?: 0) / 60,
                    distanceKm = (it.totalDistance ?: 0) / 1000.0,
                    fare = it.fare?.regular?.totalFare ?: 0,
                    path = pathSegments
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * [보행자 경로 탐색] (단독 호출용)
     * 도보 전용 모드일 때 사용합니다.
     */
    suspend fun fetchTmapWalkRoute(sx: Double, sy: Double, dx: Double, dy: Double): TmapCarRoute = withContext(Dispatchers.IO) {
        try {
            val resp = walkApi.getWalkRoute(sx, sy, dx, dy, "Start", "End")
            val rawFeatures = resp.features.orEmpty()
            if (rawFeatures.isEmpty()) return@withContext TmapCarRoute(emptyList(), 0.0, 0, 0)

            val props = rawFeatures.first().properties
            val points = rawFeatures.filter { it.geometry is TmapGeometry.LineString }
                .flatMap { (it.geometry as TmapGeometry.LineString).coords.map { c -> LatLng.from(c.lat, c.lon) } }

            TmapCarRoute(points, (props?.totalDistance ?: 0) / 1000.0, (props?.totalTime ?: 0) / 60, 0)
        } catch (e: Exception) {
            TmapCarRoute(emptyList(), 0.0, 0, 0)
        }
    }

    /**
     * [합류 지점 작명 (Naming)]
     * 특정 좌표(lat, lon)가 주어졌을 때, 사용자에게 보여줄 가장 적절한 이름(역, 건물, 주소 등)을 찾아냅니다.
     * 우선순위: 지하철역(300m) > 카페(100m) > 편의점(100m) > 주소
     */
    suspend fun getBestMeetPlaceName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val x = lon.toString()
        val y = lat.toString()
        try {
            // 1. 지하철역 검색 (만남의 장소로 가장 적합)
            val subway = localApi.searchByCategory("SW8", x, y, radius = 300).documents.firstOrNull()
            if (subway != null) return@withContext "${subway.placeName} 부근"

            // 2. 카페 검색
            val cafe = localApi.searchByCategory("CE7", x, y, radius = 100).documents.firstOrNull()
            if (cafe != null) return@withContext "${cafe.placeName} 앞"

            // 3. 편의점 검색
            val store = localApi.searchByCategory("CS2", x, y, radius = 100).documents.firstOrNull()
            if (store != null) return@withContext "${store.placeName} 앞"

            // 4. 주소 변환 (최후의 수단)
            // ★ [수정] 인터페이스 변경에 맞춰 API 키를 첫 번째 인자로 전달합니다.
            // NetworkModule이 이미 헤더를 관리하고 있지만, 메서드 시그니처가 바뀌었으므로 값을 넣어줘야 합니다.
            val addressData = localApi.coord2address(
                "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}",
                x,
                y
            ).documents.firstOrNull()
            val roadAddr = addressData?.roadAddress?.addressName
            val jibunAddr = addressData?.address?.addressName

            return@withContext roadAddr ?: jibunAddr ?: "지도 위 합류 지점"
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "합류 지점"
        }
    }

    /**
     * [주변 카테고리 검색]
     * 중심점(x, y) 반경 radius 미터 내의 특정 카테고리 장소들을 찾습니다.
     * @param categoryCode SW8(역), CE7(카페) 등
     */
    suspend fun searchCategory(
        categoryCode: String,
        x: Double,
        y: Double,
        radius: Int = 2000 // 기본 2km 반경
    ): List<Place> = withContext(Dispatchers.IO) {
        try {
            val response = localApi.searchByCategory(
                categoryCode = categoryCode,
                x = x.toString(),
                y = y.toString(),
                radius = radius,
                sort = "distance" // 거리순 정렬
            )
            response.documents
        } catch (e: Exception) {
            emptyList()
        }
    }
}