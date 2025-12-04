package com.example.gachiga.util

import com.kakao.vectormap.LatLng
import com.example.gachiga.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * [경로 계산 및 로직 총괄 매니저]
 * - 역할: API 호출, 그룹핑, 시간 계산, 텍스트 생성, 지도 그리기 지시 등 모든 비즈니스 로직을 수행합니다.
 */
class RouteLogicManager(private val repository: RouteRepository) {

    /**
     * [핵심] 전체 경로 계산 프로세스 실행
     */
    suspend fun calculateRoutes(
        members: List<Member>,
        destX: Double,
        destY: Double,
        targetTime: Calendar?,
        visualizer: RouteVisualizer
    ): String = withContext(Dispatchers.IO) {

        // 1. 초기화
        withContext(Dispatchers.Main) { visualizer.clear() }

        val logBuilder = StringBuilder()
        val allRouteMap = mutableMapOf<Int, TransitPathSegment>()
        val rawTransitPaths = mutableMapOf<Int, List<TransitPathSegment>>()
        val allPointsForCamera = mutableListOf<LatLng>()

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val now = Calendar.getInstance()

        // ---------------------------------------------------------
        // [Phase 1] API 데이터 수집
        // ---------------------------------------------------------
        for (u in members) {
            val sx = u.x ?: continue
            val sy = u.y ?: continue
            var segment: TransitPathSegment? = null

            try {
                when (u.mode) {
                    TravelMode.CAR -> {
                        val tmap = repository.fetchTmapCarRoute(sx, sy, destX, destY, u.searchOption)
                        if (tmap.points.isNotEmpty()) {
                            segment = TransitPathSegment(tmap.points, "CAR", null, "자동차", emptyList(), (tmap.km * 1000).toInt(), tmap.minutes * 60, tmap.toll)
                        }
                    }
                    TravelMode.TRANSIT -> {
                        val list = repository.fetchTransitOptions(sx, sy, destX, destY, u.searchOption)
                        val best = list.firstOrNull()
                        if (best?.path != null) {
                            val mergedPoints = best.path.flatMap { it.points }
                            val mergedStations = best.path.flatMap { it.stations }
                            segment = TransitPathSegment(mergedPoints, "TRANSIT", null, best.title, mergedStations, (best.distanceKm * 1000).toInt(), best.minutes * 60, best.fare)
                            rawTransitPaths[u.id] = best.path
                        }
                    }
                    TravelMode.WALK -> {
                        val walk = repository.fetchTmapWalkRoute(sx, sy, destX, destY)
                        if (walk.points.isNotEmpty()) {
                            segment = TransitPathSegment(walk.points, "WALK", null, "도보", emptyList(), (walk.km * 1000).toInt(), walk.minutes * 60)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (segment != null) {
                allRouteMap[u.id] = segment
                allPointsForCamera.addAll(segment.points)
            }
        }

        if (allPointsForCamera.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                visualizer.moveCameraToFit(allPointsForCamera)
            }
        }

        // ---------------------------------------------------------
        // [Phase 2] 그룹 분석 및 결과 처리
        // ---------------------------------------------------------

        val groups = RouteOptimizer.findGroups(members, allRouteMap)

        if (targetTime != null) {
            logBuilder.append("⏰ 도착 시간: ${timeFormat.format(targetTime.time)}\n")
        }
        logBuilder.append("✨ [그룹 분석 결과] (${groups.size}개 그룹)\n")
        logBuilder.append("--------------------------------\n")

        withContext(Dispatchers.Main) {
            for ((index, group) in groups.withIndex()) {

                // [Case A] 혼자 이동하는 경우
                if (group.size <= 1) {
                    val solo = group.first()
                    val route = allRouteMap[solo.id]
                    if (route != null) {
                        // 혼자니까 상세 경로 끝까지 출력 (limit = null)
                        appendUserLog(logBuilder, solo, route, rawTransitPaths[solo.id], limitStationName = null)
                        if (targetTime != null) {
                            val departTime = (targetTime.clone() as Calendar).apply {
                                add(Calendar.SECOND, -route.sectionTimeSeconds)
                            }
                            appendTimeLog(logBuilder, departTime, now, timeFormat)
                        }
                        logBuilder.append("\n")

                        if (solo.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[solo.id] ?: emptyList(), Int.MAX_VALUE, solo.color)
                        } else {
                            visualizer.drawPolyline(route.points, solo.color)
                        }
                    }
                    continue
                }

                // [Case B] 그룹 이동 (합류)

                val leader = RouteOptimizer.decideLeader(group, allRouteMap)
                val leaderRoute = allRouteMap[leader?.id]
                if (leader == null || leaderRoute == null) continue

                val followerMeetInfos = mutableMapOf<Int, Pair<LatLng, String>>()
                val pickupTasks = mutableListOf<String>()

                var leaderStartTime: Calendar? = null
                if (targetTime != null) {
                    leaderStartTime = (targetTime.clone() as Calendar).apply {
                        add(Calendar.SECOND, -leaderRoute.sectionTimeSeconds)
                    }
                }

                for (member in group) {
                    if (member.id == leader.id) continue
                    val memberRoute = allRouteMap[member.id] ?: continue

                    // 합류 지점 탐색
                    var finalMeetPoint: LatLng? = null
                    var meetName = ""

                    val commonStation = RouteMath.findCommonStation(leaderRoute.stations, memberRoute.stations)
                    if (commonStation != null) {
                        meetName = commonStation.name
                        finalMeetPoint = LatLng.from(commonStation.lat, commonStation.lon)
                    }
                    if (finalMeetPoint == null) {
                        if (member.mode == TravelMode.TRANSIT && leader.mode == TravelMode.CAR) {
                            val dest = leaderRoute.points.last()
                            val nearStation = memberRoute.stations.find {
                                RouteMath.haversineMeters(LatLng.from(it.lat, it.lon), dest) > 500 &&
                                        RouteMath.isStationNearPath(it, leaderRoute.points)
                            }
                            if (nearStation != null) {
                                meetName = nearStation.name
                                finalMeetPoint = LatLng.from(nearStation.lat, nearStation.lon)
                            }
                        } else if (member.mode == TravelMode.WALK && leader.mode == TravelMode.CAR) {
                            val firstMeetPoint = memberRoute.points.find { point ->
                                RouteMath.isStationNearPath(StationPoint("Check", point.latitude, point.longitude), leaderRoute.points, 50.0)
                            }
                            if (firstMeetPoint != null) {
                                meetName = repository.getBestMeetPlaceName(firstMeetPoint.latitude, firstMeetPoint.longitude)
                                finalMeetPoint = firstMeetPoint
                            }
                        }
                    }
                    if (finalMeetPoint == null) {
                        val shared = RouteMath.findAllSharedSegments(listOf(leaderRoute.points, memberRoute.points))
                        if (shared.isNotEmpty()) {
                            val mp = shared.first().first()
                            val dest = leaderRoute.points.last()
                            if (RouteMath.haversineMeters(mp, dest) > 500) {
                                meetName = repository.getBestMeetPlaceName(mp.latitude, mp.longitude)
                                finalMeetPoint = mp
                            }
                        }
                    }

                    if (finalMeetPoint != null) {
                        followerMeetInfos[member.id] = Pair(finalMeetPoint, meetName)

                        var timeMsg = ""
                        if (leaderStartTime != null) {
                            val idx = RouteMath.findNearestPathIndex(leaderRoute.points, finalMeetPoint)
                            val timeToMeet = RouteMath.estimateTimeFromStart(leaderRoute.points, idx, leaderRoute.distanceMeters, leaderRoute.sectionTimeSeconds)
                            val meetTime = (leaderStartTime.clone() as Calendar).apply { add(Calendar.SECOND, timeToMeet) }
                            timeMsg = " (${timeFormat.format(meetTime.time)})"
                        }

                        val action = if (leader.mode == TravelMode.CAR) "픽업" else "합류"
                        val suffix = if (leader.mode == TravelMode.CAR) "탑승" else "만남"
                        pickupTasks.add("$action: $meetName$timeMsg (${member.name} $suffix)")
                    }
                }

                // -----------------------------------------------------
                // [로그 출력 - 대장] (왕관 유지)
                // -----------------------------------------------------
                logBuilder.append("👑 ${leader.name} (대장)\n")
                appendBasicInfo(logBuilder, leader, leaderRoute)
                if (leaderStartTime != null) appendTimeLog(logBuilder, leaderStartTime, now, timeFormat)

                // 대장 상세 경로 (끝까지 출력)
                if (leader.mode == TravelMode.TRANSIT) {
                    logBuilder.append("   ㄴ 경로 상세:\n")
                    // ★ 기존 joinToString 삭제 -> generateDetailedPathLog(..., null) 사용
                    generateDetailedPathLog(logBuilder, rawTransitPaths[leader.id] ?: emptyList(), null)
                }

                if (pickupTasks.isNotEmpty()) {
                    pickupTasks.forEach { task -> logBuilder.append("   ㄴ 🔔 $task\n") }
                } else {
                    logBuilder.append("   ㄴ (합류 가능한 팔로워 없음)\n")
                }
                logBuilder.append("\n")

                // 대장 그리기
                if (leader.mode == TravelMode.TRANSIT) {
                    visualizer.drawTransitRouteCut(rawTransitPaths[leader.id] ?: emptyList(), Int.MAX_VALUE, leader.color)
                } else {
                    visualizer.drawPolyline(leaderRoute.points, leader.color)
                }

                // -----------------------------------------------------
                // [로그 출력 - 팔로워] (달리기, 합류정보 유지)
                // -----------------------------------------------------
                for (member in group) {
                    if (member.id == leader.id) continue
                    val memberRoute = allRouteMap[member.id] ?: continue
                    val meetInfo = followerMeetInfos[member.id]

                    logBuilder.append("🏃 ${member.name} (팔로워)\n")
                    appendBasicInfo(logBuilder, member, memberRoute)

                    if (meetInfo != null) {
                        val (meetPoint, meetName) = meetInfo

                        if (leaderStartTime != null) {
                            val lIdx = RouteMath.findNearestPathIndex(leaderRoute.points, meetPoint)
                            val lTime = RouteMath.estimateTimeFromStart(leaderRoute.points, lIdx, leaderRoute.distanceMeters, leaderRoute.sectionTimeSeconds)
                            val meetTime = (leaderStartTime.clone() as Calendar).apply { add(Calendar.SECOND, lTime) }

                            val fIdx = RouteMath.findNearestPathIndex(memberRoute.points, meetPoint)
                            val fTime = RouteMath.estimateTimeFromStart(memberRoute.points, fIdx, memberRoute.distanceMeters, memberRoute.sectionTimeSeconds)

                            val departTime = (meetTime.clone() as Calendar).apply {
                                add(Calendar.SECOND, -fTime)
                                add(Calendar.MINUTE, -5)
                            }
                            appendTimeLog(logBuilder, departTime, now, timeFormat)
                            logBuilder.append("   ㄴ 💡 합류 시간: ${timeFormat.format(meetTime.time)} 합류 예정 (5분 대기)\n")
                        }

                        // 팔로워 상세 경로 (합류 지점까지만 출력)
                        if (member.mode == TravelMode.TRANSIT) {
                            logBuilder.append("   ㄴ 경로 상세:\n")
                            // ★ 기존 generateCutPathString 삭제 -> generateDetailedPathLog(..., meetName) 사용
                            generateDetailedPathLog(logBuilder, rawTransitPaths[member.id] ?: emptyList(), meetName)
                        } else {
                            logBuilder.append("   ㄴ 경로: 도보 이동 > $meetName (합류)\n")
                        }

                        // 그리기 및 빨간선
                        val cutIdx = RouteMath.findNearestPathIndex(memberRoute.points, meetPoint)
                        if (member.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[member.id] ?: emptyList(), cutIdx, member.color)
                        } else {
                            if (cutIdx != -1) {
                                val cutPath = memberRoute.points.take(cutIdx + 1)
                                visualizer.drawPolyline(cutPath, member.color)
                            }
                        }

                        val leaderCutIdx = RouteMath.findNearestPathIndex(leaderRoute.points, meetPoint)
                        if (leaderCutIdx != -1) {
                            val isTransitLeader = (leader.mode == TravelMode.TRANSIT)
                            visualizer.drawRedLine(leaderRoute.points.drop(leaderCutIdx), isTransitLeader)
                        }

                    } else {
                        // 합류 실패
                        logBuilder.append("   ㄴ (합류 실패: 각자 이동)\n")
                        if (targetTime != null) {
                            val departTime = (targetTime.clone() as Calendar).apply {
                                add(Calendar.SECOND, -memberRoute.sectionTimeSeconds)
                            }
                            appendTimeLog(logBuilder, departTime, now, timeFormat)
                        }
                        if (member.mode == TravelMode.TRANSIT) {
                            logBuilder.append("   ㄴ 경로 상세:\n")
                            generateDetailedPathLog(logBuilder, rawTransitPaths[member.id] ?: emptyList(), null)
                            visualizer.drawTransitRouteCut(rawTransitPaths[member.id] ?: emptyList(), Int.MAX_VALUE, member.color)
                        } else {
                            visualizer.drawPolyline(memberRoute.points, member.color)
                        }
                    }
                    logBuilder.append("\n")
                }
            }
        }
        return@withContext logBuilder.toString()
    }

    // --- Helper Functions ---

    private fun appendTimeLog(sb: StringBuilder, departTime: Calendar, now: Calendar, fmt: java.text.SimpleDateFormat) {
        val timeStr = fmt.format(departTime.time)
        sb.append("   ㄴ ⏰ 출발: $timeStr")
        if (departTime.before(now)) {
            val diff = (now.timeInMillis - departTime.timeInMillis) / (1000 * 60)
            sb.append(" (⚠️ 지각! ${diff}분 전 출발했어야 함)\n")
        } else {
            sb.append("\n")
        }
    }

    private fun appendBasicInfo(sb: StringBuilder, u: Member, route: TransitPathSegment) {
        val distKm = route.distanceMeters / 1000.0
        val min = route.sectionTimeSeconds / 60
        val fare = if (u.mode == TravelMode.CAR || route.totalFare > 0) {
            " / ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(route.totalFare)}원"
        } else {
            ""
        }
        sb.append("   ㄴ 정보: ${"%.1f".format(distKm)}km / ${min}분$fare\n")
    }

    /** * [혼자 이동 시 사용]
     * 내부적으로 generateDetailedPathLog를 호출하여 포맷을 통일함
     */
    private fun appendUserLog(sb: StringBuilder, u: Member, route: TransitPathSegment, rawPaths: List<TransitPathSegment>?, limitStationName: String?) {
        val modeKorean = when(u.mode) {
            TravelMode.CAR -> "자동차"
            TravelMode.TRANSIT -> "대중교통"
            TravelMode.WALK -> "도보"
        }
        sb.append("${u.name} ($modeKorean)\n")
        appendBasicInfo(sb, u, route)

        if (u.mode == TravelMode.TRANSIT && rawPaths != null) {
            sb.append("   ㄴ 경로 상세:\n")
            generateDetailedPathLog(sb, rawPaths, limitStationName)
        } else if (limitStationName != null && u.mode != TravelMode.TRANSIT) {
            sb.append("   ㄴ 경로: $limitStationName 에서 합류!\n")
        }
    }

    /**
     * [상세 경로 생성 로직 (통일됨)]
     * - 혼자/대장: limitStationName = null (끝까지 출력)
     * - 팔로워: limitStationName = 합류역 (거기서 멈춤)
     */
    private fun generateDetailedPathLog(sb: StringBuilder, paths: List<TransitPathSegment>, limitStationName: String?) {
        for ((index, segment) in paths.withIndex()) {
            val stepNum = index + 1
            var shouldStop = false

            if (segment.mode == "WALK") {
                val distStr = if (segment.distanceMeters > 0) "${segment.distanceMeters}m" else "이동"

                // 다음 역 이름 추론
                val nextSeg = paths.getOrNull(index + 1)
                var nextStationName = nextSeg?.stations?.firstOrNull()?.name

                if (nextStationName != null) {
                    if (nextSeg?.mode == "SUBWAY") {
                        if (!nextStationName.endsWith("역")) nextStationName += "역"
                    } else if (nextSeg?.mode == "BUS") {
                        nextStationName += " 정류장"
                    }
                }

                // 합류 지점 체크 (도보 목적지가 합류점일 때)
                if (limitStationName != null && nextStationName?.contains(limitStationName) == true) {
                    sb.append("      $stepNum. 도보($distStr) → $nextStationName (여기서 합류!)\n")
                    shouldStop = true
                } else {
                    val destInfo = if (nextStationName != null) " → $nextStationName" else ""
                    sb.append("      $stepNum. 도보($distStr)$destInfo\n")
                }

            } else {
                // [버스/지하철]
                val type = if (segment.mode == "BUS") "버스" else "지하철"
                val routeName = segment.name ?: ""

                // 정류장 리스트 준비
                var displayStations = segment.stations.map { it.name }.filter { it.isNotBlank() }

                // 합류 지점 체크
                if (limitStationName != null) {
                    val cutIndex = displayStations.indexOfFirst { it.contains(limitStationName) }
                    if (cutIndex != -1) {
                        // 합류 지점까지만 자름
                        displayStations = displayStations.take(cutIndex + 1)
                        shouldStop = true
                    }
                }

                val stationListStr = displayStations.joinToString(", ")
                val suffix = if (shouldStop) " (여기서 합류!)" else ""

                sb.append("      $stepNum. $type($routeName): [$stationListStr]$suffix\n")
            }

            if (shouldStop) break
        }
    }

    /**
     * [무게중심 기반 추천]
     * 멤버들의 좌표 평균(Center)을 구하고, 카테고리별로 상위 3개씩 추천합니다.
     */
    suspend fun recommendMidpointPlaces(members: List<Member>): List<SuggestedRoute> = withContext(Dispatchers.IO) {
        // 1. 유효한 좌표를 가진 멤버만 필터링
        val validMembers = members.filter { it.x != null && it.y != null }
        if (validMembers.isEmpty()) return@withContext emptyList()

        // 2. 무게중심(Centroid) 계산
        val avgX = validMembers.map { it.x!! }.average()
        val avgY = validMembers.map { it.y!! }.average()

        // 3. 추천 카테고리 정의 (지하철, 카페, 음식점)
        val targetCategories = listOf("SW8", "CE7", "FD6")
        val results = mutableListOf<SuggestedRoute>()

        // 4. 카테고리별 검색 및 변환
        for (code in targetCategories) {
            // ★ 여기서 .take(3)으로 3개만 가져옵니다.
            val places = repository.searchCategory(code, avgX, avgY, 2000).take(3)

            places.forEach { place ->
                results.add(
                    SuggestedRoute(
                        id = "REC_${place.placeName}", // 고유 ID 생성
                        placeName = place.placeName,
                        address = place.roadAddressName.ifBlank { place.addressName },
                        latitude = place.latitude.toDouble(),
                        longitude = place.longitude.toDouble(),

                        // ★ 주의: 여기엔 '모두를 위한 정보'만 넣어야 합니다.
                        // 개인별 소요시간/비용은 여기서 계산할 수 없습니다. (아래 설명 참조)
                        totalTime = "추천 장소",
                        totalFee = getCategoryName(code), // 예: "지하철역", "카페"
                        description = "멤버들의 중간 지점 반경 2km 내 추천 장소입니다."
                    )
                )
            }
        }

        return@withContext results
    }

    // 카테고리 코드 -> 한글 이름 변환 헬퍼
    private fun getCategoryName(code: String): String {
        return when(code) {
            "SW8" -> "지하철역"
            "CE7" -> "카페"
            "FD6" -> "음식점"
            "AT4" -> "관광명소"
            "CT1" -> "문화시설"
            else -> "장소"
        }
    }
}