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
        visualizer: RouteVisualizer,
        myMemberId: Int? = null // 내 아이디
    ): CalculationResult = withContext(Dispatchers.IO) {

        // 1. 초기화
        withContext(Dispatchers.Main) { visualizer.clear() }

        val logBuilder = StringBuilder()
        val myLogBuilder = StringBuilder()
        var myPathPoints: List<LatLng>? = null // 내 파란 경로 (합류 전)
        var myRedPathPoints: List<LatLng>? = null // ★ [추가] 내 빨간 경로 (합류 후)

        val allRouteMap = mutableMapOf<Int, TransitPathSegment>()
        val rawTransitPaths = mutableMapOf<Int, List<TransitPathSegment>>()
        val allPointsForCamera = mutableListOf<LatLng>()

        val redLinesCollector = mutableListOf<Pair<List<LatLng>, Boolean>>()
        // ★ [추가] 각 멤버별 경로 자르는 위치 저장용
        val cutIndicesCollector = mutableMapOf<Int, Int>()

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val now = Calendar.getInstance()

        // [Phase 1] API 데이터 수집 (작성자님이 주신 코드 그대로 사용)
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
                            // (작성자님이 주신 갭 채우기 로직)
                            val filledPath = best.path.mapIndexed { index, segment ->
                                if (segment.mode == "WALK" && segment.points.isEmpty()) {
                                    val startPos = if (index == 0) LatLng.from(sy, sx) else best.path[index - 1].points.lastOrNull() ?: LatLng.from(sy, sx)
                                    val endPos = if (index == best.path.lastIndex) LatLng.from(destY, destX) else best.path[index + 1].points.firstOrNull() ?: LatLng.from(destY, destX)
                                    try {
                                        val detailedWalk = repository.fetchTmapWalkRoute(sx = startPos.longitude, sy = startPos.latitude, dx = endPos.longitude, dy = endPos.latitude)
                                        if (detailedWalk.points.isNotEmpty()) segment.copy(points = detailedWalk.points) else segment.copy(points = listOf(startPos, endPos))
                                    } catch (e: Exception) { segment.copy(points = listOf(startPos, endPos)) }
                                } else if ((segment.mode == "BUS" || segment.mode == "EXPRESSBUS") && segment.points.size <= 2) {
                                    val startPos = segment.points.first()
                                    val endPos = segment.points.last()
                                    try {
                                        val carPath = repository.fetchTmapCarRoute(startPos.longitude, startPos.latitude, endPos.longitude, endPos.latitude, 0)
                                        if (carPath.points.isNotEmpty()) segment.copy(points = carPath.points) else segment
                                    } catch (e: Exception) { segment }
                                } else { segment }
                            }
                            val mergedPoints = filledPath.flatMap { it.points }
                            val mergedStations = filledPath.flatMap { it.stations }
                            segment = TransitPathSegment(mergedPoints, "TRANSIT", null, best.title, mergedStations, (best.distanceKm * 1000).toInt(), best.minutes * 60, best.fare)
                            rawTransitPaths[u.id] = filledPath
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
            withContext(Dispatchers.Main) { visualizer.moveCameraToFit(allPointsForCamera) }
        }

        // [Phase 2] 그룹 분석 및 결과 처리
        val groups = RouteOptimizer.findGroups(members, allRouteMap)

        if (targetTime != null) {
            logBuilder.append("⏰ 도착 시간: ${timeFormat.format(targetTime.time)}\n")
        }
        logBuilder.append("✨ [그룹 분석 결과] (${groups.size}개 그룹)\n")
        logBuilder.append("--------------------------------\n")

        withContext(Dispatchers.Main) {
            for ((index, group) in groups.withIndex()) {

                // [Case A] 혼자 이동
                if (group.size <= 1) {
                    val solo = group.first()
                    val route = allRouteMap[solo.id]
                    if (route != null) {
                        val soloLog = buildString {
                            appendUserLog(this, solo, route, rawTransitPaths[solo.id], limitStationName = null)
                            if (targetTime != null) {
                                val departTime = (targetTime.clone() as Calendar).apply { add(Calendar.SECOND, -route.sectionTimeSeconds) }
                                appendTimeLog(this, departTime, now, timeFormat)
                            }
                            append("\n")
                        }
                        logBuilder.append(soloLog)

                        // ★ [수정] 내가 솔로라면 전체가 내 파란 경로
                        if (solo.id == myMemberId) {
                            myLogBuilder.append(soloLog)
                            myPathPoints = route.points
                            // 빨간 경로는 없음 (null)
                        }

                        if (solo.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[solo.id] ?: emptyList(), Int.MAX_VALUE, solo.color)
                        } else {
                            visualizer.drawPolyline(route.points, solo.color)
                        }
                    }
                    continue
                }

                // [Case B] 그룹 이동
                val leader = RouteOptimizer.decideLeader(group, allRouteMap)
                val leaderRoute = allRouteMap[leader?.id]
                if (leader == null || leaderRoute == null) continue

                val followerMeetInfos = mutableMapOf<Int, Pair<LatLng, String>>()
                val pickupTasks = mutableListOf<String>()
                var leaderStartTime: Calendar? = null
                if (targetTime != null) {
                    leaderStartTime = (targetTime.clone() as Calendar).apply { add(Calendar.SECOND, -leaderRoute.sectionTimeSeconds) }
                }

                // ★ 대장이 빨간선으로 변하는 시점(가장 빠른 합류점) 찾기용
                var earliestLeaderCutIdx = Int.MAX_VALUE

                for (member in group) {
                    if (member.id == leader.id) continue
                    val memberRoute = allRouteMap[member.id] ?: continue
                    var finalMeetPoint: LatLng? = null
                    var meetName = ""

                    // (합류 지점 찾기 로직 - 기존 동일)
                    val commonStation = RouteMath.findCommonStation(leaderRoute.stations, memberRoute.stations)
                    if (commonStation != null) {
                        meetName = commonStation.name
                        finalMeetPoint = LatLng.from(commonStation.lat, commonStation.lon)
                    }
                    if (finalMeetPoint == null) {
                        if (member.mode == TravelMode.TRANSIT && leader.mode == TravelMode.CAR) {
                            val dest = leaderRoute.points.last()
                            val nearStation = memberRoute.stations.find {
                                RouteMath.haversineMeters(LatLng.from(it.lat, it.lon), dest) > 500 && RouteMath.isStationNearPath(it, leaderRoute.points)
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
                // [로그 출력 - 대장]
                // -----------------------------------------------------
                val leaderLog = buildString {
                    append("👑 ${leader.name} (대장)\n")
                    appendBasicInfo(this, leader, leaderRoute)
                    if (leaderStartTime != null) appendTimeLog(this, leaderStartTime, now, timeFormat)
                    if (leader.mode == TravelMode.TRANSIT) {
                        append("   ㄴ 경로 상세:\n")
                        generateDetailedPathLog(this, rawTransitPaths[leader.id] ?: emptyList(), null)
                    }
                    if (pickupTasks.isNotEmpty()) {
                        pickupTasks.forEach { task -> append("   ㄴ 🔔 $task\n") }
                    } else {
                        append("   ㄴ (합류 가능한 팔로워 없음)\n")
                    }
                    append("\n")
                }
                logBuilder.append(leaderLog)

                // -----------------------------------------------------
                // [로그 출력 - 팔로워] & 데이터 수집
                // -----------------------------------------------------
                for (member in group) {
                    if (member.id == leader.id) continue
                    val memberRoute = allRouteMap[member.id] ?: continue
                    val meetInfo = followerMeetInfos[member.id]

                    val followerLog = buildString {
                        append("🏃 ${member.name} (팔로워)\n")
                        appendBasicInfo(this, member, memberRoute)
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
                                appendTimeLog(this, departTime, now, timeFormat)
                                append("   ㄴ 💡 합류 시간: ${timeFormat.format(meetTime.time)} 합류 예정 (5분 대기)\n")
                            }
                            if (member.mode == TravelMode.TRANSIT) {
                                append("   ㄴ 경로 상세:\n")
                                generateDetailedPathLog(this, rawTransitPaths[member.id] ?: emptyList(), meetName)
                            } else {
                                append("   ㄴ 경로: 도보 이동 > $meetName (합류)\n")
                            }
                        } else {
                            append("   ㄴ (합류 실패: 각자 이동)\n")
                            if (targetTime != null) {
                                val departTime = (targetTime.clone() as Calendar).apply { add(Calendar.SECOND, -memberRoute.sectionTimeSeconds) }
                                appendTimeLog(this, departTime, now, timeFormat)
                            }
                            if (member.mode == TravelMode.TRANSIT) {
                                append("   ㄴ 경로 상세:\n")
                                generateDetailedPathLog(this, rawTransitPaths[member.id] ?: emptyList(), null)
                            }
                        }
                        append("\n")
                    }
                    logBuilder.append(followerLog)

                    // ★ [핵심 로직] 팔로워 데이터 수집 (그리기 & 자르기)
                    if (meetInfo != null) {
                        val (meetPoint, meetName) = meetInfo

                        // 1. 내 경로 자를 위치(cutIdx) 계산
                        val cutIdx = RouteMath.findNearestPathIndex(memberRoute.points, meetPoint)

                        // ★ [저장] 나중에 전체 화면 복구할 때 여기서 자름!
                        cutIndicesCollector[member.id] = cutIdx

                        // 2. 대장 경로 자를 위치(leaderCutIdx) 계산
                        val leaderCutIdx = RouteMath.findNearestPathIndex(leaderRoute.points, meetPoint)
                        if (leaderCutIdx != -1 && leaderCutIdx < earliestLeaderCutIdx) {
                            earliestLeaderCutIdx = leaderCutIdx // 대장도 여기서부터 빨개져야 함
                        }

                        // ★ [추가] 내가 이 팔로워라면?
                        if (member.id == myMemberId) {
                            myLogBuilder.append(followerLog)

                            // [파란 구간] 출발지 ~ 합류지
                            myPathPoints = if (cutIdx != -1) memberRoute.points.take(cutIdx + 1) else memberRoute.points

                            // [빨간 구간] 합류지 ~ 목적지 (대장 경로를 빌려옴)
                            if (leaderCutIdx != -1) {
                                myRedPathPoints = leaderRoute.points.drop(leaderCutIdx)
                            }
                        }

                        // [그리기] 팔로워 파란선 (잘라서 그림)
                        if (member.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[member.id] ?: emptyList(), cutIdx, member.color)
                        } else {
                            if (cutIdx != -1) {
                                visualizer.drawPolyline(memberRoute.points.take(cutIdx + 1), member.color)
                            }
                        }

                        // [그리기] 빨간 합류선
                        if (leaderCutIdx != -1) {
                            val isTransitLeader = (leader.mode == TravelMode.TRANSIT)
                            val redPoints = leaderRoute.points.drop(leaderCutIdx)
                            visualizer.drawRedLine(redPoints, isTransitLeader)
                            redLinesCollector.add(Pair(redPoints, isTransitLeader))
                        }

                    } else {
                        // 합류 실패 시
                        if (member.id == myMemberId) {
                            myLogBuilder.append(followerLog)
                            myPathPoints = memberRoute.points
                        }
                        if (member.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[member.id] ?: emptyList(), Int.MAX_VALUE, member.color)
                        } else {
                            visualizer.drawPolyline(memberRoute.points, member.color)
                        }
                    }
                } // End of Follower Loop

                // ★ [핵심 로직] 대장 데이터 수집 (루프 끝난 후 처리)
                // 대장은 가장 빨리 만난 지점(earliestLeaderCutIdx)부터 빨개집니다.
                if (earliestLeaderCutIdx != Int.MAX_VALUE) {
                    // ★ [저장] 전체 화면 복구 시 대장도 여기서 잘라야 빨간선과 안 겹침!
                    cutIndicesCollector[leader.id] = earliestLeaderCutIdx

                    if (leader.id == myMemberId) {
                        myLogBuilder.append(leaderLog)

                        // [파란 구간] 출발 ~ 첫 합류
                        myPathPoints = leaderRoute.points.take(earliestLeaderCutIdx + 1)
                        // [빨간 구간] 첫 합류 ~ 목적지
                        myRedPathPoints = leaderRoute.points.drop(earliestLeaderCutIdx)
                    }

                    // [그리기] 대장 파란선 (잘라서 그림)
                    if (leader.mode == TravelMode.TRANSIT) {
                        visualizer.drawTransitRouteCut(rawTransitPaths[leader.id] ?: emptyList(), earliestLeaderCutIdx, leader.color)
                    } else {
                        visualizer.drawPolyline(leaderRoute.points.take(earliestLeaderCutIdx + 1), leader.color)
                    }
                } else {
                    // 아무도 안 태우고 혼자 가는 대장
                    if (leader.id == myMemberId) {
                        myLogBuilder.append(leaderLog)
                        myPathPoints = leaderRoute.points
                    }
                    if (leader.mode == TravelMode.TRANSIT) {
                        visualizer.drawTransitRouteCut(rawTransitPaths[leader.id] ?: emptyList(), Int.MAX_VALUE, leader.color)
                    } else {
                        visualizer.drawPolyline(leaderRoute.points, leader.color)
                    }
                }
            }
        }

        return@withContext CalculationResult(
            fullLog = logBuilder.toString(),
            myLog = if (myMemberId != null) myLogBuilder.toString() else null,
            myPathPoints = myPathPoints,
            myRedPathPoints = myRedPathPoints, // ★ 담기
            allRoutes = allRouteMap,
            rawTransitPaths = rawTransitPaths,
            allPointsForCamera = allPointsForCamera,
            redLines = redLinesCollector,
            memberCutIndices = cutIndicesCollector // ★ 담기
        )
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