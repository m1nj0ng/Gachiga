package com.miujong.gachiga10.data.repository

import com.kakao.vectormap.LatLng
import com.miujong.gachiga10.data.model.*
import com.miujong.gachiga10.network.*
import com.miujong.gachiga10.util.RouteMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouteRepository {

    private val tmapRouteApi = NetworkModule.getTmapRouteApi()
    private val transitApi = NetworkModule.getTransitApi()
    private val walkApi = NetworkModule.getTmapWalkApi()
    private val localApi = NetworkModule.getLocalApi()

    // 자동차 경로 탐색
    suspend fun fetchTmapCarRoute(sx: Double, sy: Double, dx: Double, dy: Double, option: Int): TmapCarRoute = withContext(Dispatchers.IO) {
        try {
            val resp = tmapRouteApi.getCarRoute(
                startX = sx, startY = sy, endX = dx, endY = dy, searchOption = option
            )

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
            val props = resp.features.firstOrNull()?.properties
            TmapCarRoute(points, (props?.totalDistance ?: 0) / 1000.0, (props?.totalTime ?: 0) / 60, props?.totalFare ?: 0)

        } catch (e: Exception) {
            e.printStackTrace()
            TmapCarRoute(emptyList(), 0.0, 0, 0)
        }
    }

    // 대중교통 경로 탐색 (정렬 및 도보 보정 포함)
    suspend fun fetchTransitOptions(sx: Double, sy: Double, dx: Double, dy: Double, option: Int): List<TransitOption> = withContext(Dispatchers.IO) {
        try {
            val body = TransitRouteRequest(
                startX = sx.toString(), startY = sy.toString(),
                endX = dx.toString(), endY = dy.toString(),
                count = 10, format = "json"
            )
            val res = transitApi.getTransitRoutes(body)
            var rawItineraries = res.metaData?.plan?.itineraries.orEmpty()

            // 옵션별 정렬 로직
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

            rawItineraries.mapIndexed { idx, it ->
                val modes = it.legs.orEmpty().mapNotNull { leg -> leg.mode }.filter { m -> m != "WALK" }.toSet()
                val title = when {
                    modes.isEmpty() -> "도보"
                    modes.size == 1 -> modes.first()
                    else -> "복합"
                }

                // 1순위 경로만 상세 좌표 파싱
                val pathSegments = if (idx == 0) {
                    var lastPoint: LatLng? = null
                    val legs = it.legs.orEmpty()

                    legs.mapIndexed { legIdx, leg ->
                        var parsedPoints = RouteMath.parsePassShape(leg.passShape)

                        // 도보 경로 보정 (Last Mile)
                        if (parsedPoints.isEmpty() && lastPoint != null
                            && legIdx == legs.lastIndex
                            && (leg.mode == "WALK" || leg.mode == null)) {
                            try {
                                val walkResp = walkApi.getWalkRoute(
                                    startX = lastPoint!!.longitude, startY = lastPoint!!.latitude,
                                    endX = dx, endY = dy,
                                    startName = "DropOff", endName = "Destination"
                                )
                                val walkPoints = walkResp.features
                                    ?.filter { f -> f.geometry is TmapGeometry.LineString }
                                    ?.flatMap { f -> (f.geometry as TmapGeometry.LineString).coords.map { c -> LatLng.from(c.lat, c.lon) } }
                                    .orEmpty()
                                if (walkPoints.isNotEmpty()) parsedPoints = walkPoints
                            } catch (e: Exception) {
                                parsedPoints = listOf(lastPoint!!, LatLng.from(dy, dx))
                            }
                        }

                        if (parsedPoints.isEmpty() && lastPoint != null && leg.mode == "WALK") {
                            parsedPoints = listOf(lastPoint!!)
                        }

                        if (parsedPoints.isNotEmpty()) lastPoint = parsedPoints.last()

                        TransitPathSegment(
                            points = parsedPoints,
                            mode = leg.mode ?: "WALK",
                            color = leg.routeColor,
                            name = leg.route
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

    // 도보 경로 탐색
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

    // 합류 지점 명칭 추천 로직 (스마트 랜드마크 탐색)
    suspend fun getBestMeetPlaceName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val x = lon.toString()
        val y = lat.toString()

        try {
            // 1순위: 지하철역 (반경 300m 이내) - 가장 명확한 만남의 장소
            val subway = localApi.searchByCategory("SW8", x, y, radius = 300)
                .documents.firstOrNull()
            if (subway != null) return@withContext "${subway.placeName} 부근"

            // 2순위: 카페 (반경 100m 이내) - 기다리기 좋은 장소
            val cafe = localApi.searchByCategory("CE7", x, y, radius = 100)
                .documents.firstOrNull()
            if (cafe != null) return@withContext "${cafe.placeName} 앞"

            // 3순위: 편의점 (반경 100m 이내) - 찾기 쉬운 장소
            val store = localApi.searchByCategory("CS2", x, y, radius = 100)
                .documents.firstOrNull()
            if (store != null) return@withContext "${store.placeName} 앞"

            // 4순위: 주소 (도로명 우선, 없으면 지번)
            val addressData = localApi.coord2address(x, y).documents.firstOrNull()
            val roadAddr = addressData?.roadAddress?.addressName
            val jibunAddr = addressData?.address?.addressName

            return@withContext roadAddr ?: jibunAddr ?: "지도 위 합류 지점"

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "합류 지점"
        }
    }
}