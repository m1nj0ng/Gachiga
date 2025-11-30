package com.miujong.gachiga10.util

import com.kakao.vectormap.LatLng
import com.miujong.gachiga10.data.model.*
import com.miujong.gachiga10.data.repository.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * [경로 계산 및 로직 총괄 매니저]
 * - 역할: API 호출, 그룹핑, 시간 계산, 텍스트 생성, 지도 그리기 지시 등 모든 비즈니스 로직을 수행합니다.
 * - 위치: MainActivity(UI)와 Repository(Data) 사이에서 중재자 역할을 합니다.
 */
class RouteLogicManager(private val repository: RouteRepository) {

    /**
     * [핵심] 전체 경로 계산 프로세스 실행
     * 1. 모든 유저의 경로 API 호출 (Tmap)
     * 2. RouteOptimizer를 통한 그룹핑 및 대장 선정
     * 3. 도착 시간(targetTime) 기준 출발 시간 역산(Back-calculation)
     * 4. RouteVisualizer를 통해 지도에 경로 그리기 (빨간 합류선 포함)
     * 5. 최종 결과 로그(String) 생성 및 반환
     *
     * @param users 참여하는 사용자 리스트
     * @param destX 목적지 경도
     * @param destY 목적지 위도
     * @param targetTime 사용자가 설정한 목표 도착 시간 (없으면 null)
     * @param visualizer 지도 그리기를 담당하는 객체
     * @return 화면에 표시할 최종 안내 로그 문자열
     */
    suspend fun calculateRoutes(
        users: List<User>,
        destX: Double,
        destY: Double,
        targetTime: Calendar?,
        visualizer: RouteVisualizer
    ): String = withContext(Dispatchers.IO) {

        // 1. 초기화: 지도 위의 기존 선들을 모두 지웁니다.
        // (UI 작업이므로 Main Thread에서 실행)
        withContext(Dispatchers.Main) { visualizer.clear() }

        val logBuilder = StringBuilder()
        // API 결과 저장소 (Optimizer 전달용)
        val allRouteMap = mutableMapOf<Int, TransitPathSegment>()
        // 대중교통 원본 경로 저장소 (색상/텍스트 복원용)
        val rawTransitPaths = mutableMapOf<Int, List<TransitPathSegment>>()
        // 카메라 이동을 위한 전체 좌표 모음
        val allPointsForCamera = mutableListOf<LatLng>()

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val now = Calendar.getInstance()

        // ---------------------------------------------------------
        // [Phase 1] API 데이터 수집 (Data Collection)
        // - 각 사용자의 이동 수단에 맞는 TMAP API를 호출합니다.
        // ---------------------------------------------------------
        for (u in users) {
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
                            // 원본 Path(구간별 정보) 저장 -> 나중에 색상/텍스트 복원 시 사용
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

        // [Camera Update] 모든 경로가 한눈에 보이도록 카메라 줌 조정
        if (allPointsForCamera.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                visualizer.moveCameraToFit(allPointsForCamera)
            }
        }

        // ---------------------------------------------------------
        // [Phase 2] 그룹 분석 및 결과 처리 (Analysis & Rendering)
        // ---------------------------------------------------------

        // 1. 그룹핑 수행 (RouteOptimizer)
        val groups = RouteOptimizer.findGroups(users, allRouteMap)

        if (targetTime != null) {
            logBuilder.append("⏰ 목표 도착: ${timeFormat.format(targetTime.time)}\n")
        }
        logBuilder.append("✨ [그룹 분석 결과] (${groups.size}개 그룹)\n")
        logBuilder.append("--------------------------------\n")

        // 그리기 작업은 Main Thread에서 수행
        withContext(Dispatchers.Main) {
            for ((index, group) in groups.withIndex()) {
                val groupName = "그룹 ${index + 1}"

                // [Case A] 혼자 이동하는 경우
                if (group.size <= 1) {
                    val solo = group.first()
                    val route = allRouteMap[solo.id]
                    if (route != null) {
                        appendUserLog(logBuilder, solo, route, rawTransitPaths[solo.id])
                        // 시간 계산 (혼자라도 지각 여부는 체크)
                        if (targetTime != null) {
                            val departTime = (targetTime.clone() as Calendar).apply {
                                add(Calendar.SECOND, -route.sectionTimeSeconds)
                            }
                            appendTimeLog(logBuilder, departTime, now, timeFormat)
                        }
                        logBuilder.append("\n")

                        // 그리기 (자르지 않고 전체 경로 표시)
                        if (solo.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[solo.id] ?: emptyList(), Int.MAX_VALUE, solo.color)
                        } else {
                            visualizer.drawPolyline(route.points, solo.color)
                        }
                    }
                    continue
                }

                // [Case B] 그룹 이동 (합류)

                // 1. 대장(Leader) 선정
                val leader = RouteOptimizer.decideLeader(group, allRouteMap)
                val leaderRoute = allRouteMap[leader?.id]
                if (leader == null || leaderRoute == null) continue

                // 2. 팔로워별 합류 정보 사전 계산 (Pre-calculation)
                // (대장 로그에 '픽업 리스트'를 띄우기 위해 미리 계산함)
                val followerMeetInfos = mutableMapOf<Int, Pair<LatLng, String>>()
                val pickupTasks = mutableListOf<String>()

                // 대장의 출발 시각 역산 (도착 시간 - 소요 시간)
                var leaderStartTime: Calendar? = null
                if (targetTime != null) {
                    leaderStartTime = (targetTime.clone() as Calendar).apply {
                        add(Calendar.SECOND, -leaderRoute.sectionTimeSeconds)
                    }
                }

                for (member in group) {
                    if (member.id == leader.id) continue
                    val memberRoute = allRouteMap[member.id] ?: continue

                    // 합류 지점 탐색 (이름 -> 근접 -> 좌표 순서)
                    var finalMeetPoint: LatLng? = null
                    var meetName = ""

                    // (1) 이름 매칭
                    val commonStation = RouteMath.findCommonStation(leaderRoute.stations, memberRoute.stations)
                    if (commonStation != null) {
                        meetName = commonStation.name
                        finalMeetPoint = LatLng.from(commonStation.lat, commonStation.lon)
                    }
                    // (2) 근접 매칭 (스침/교차)
                    if (finalMeetPoint == null) {
                        if (member.mode == TravelMode.TRANSIT && leader.mode == TravelMode.CAR) {
                            val dest = leaderRoute.points.last()
                            val nearStation = memberRoute.stations.find {
                                // [납치 방지] 목적지 500m 이내 제외
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
                    // (3) 좌표 매칭 (도로 겹침)
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

                        // 대장이 합류점에 도착하는 시간 계산 (표시용)
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
                // [로그 출력 & 그리기 - 대장]
                // -----------------------------------------------------
                logBuilder.append("👑 ${leader.name} (대장)\n")
                appendBasicInfo(logBuilder, leader, leaderRoute)
                if (leaderStartTime != null) appendTimeLog(logBuilder, leaderStartTime, now, timeFormat)

                // 대중교통인 경우 상세 경로 출력
                if (leader.mode == TravelMode.TRANSIT) {
                    val fullPath = rawTransitPaths[leader.id] ?: emptyList()
                    val pathStr = fullPath.joinToString(" > ") {
                        when (it.mode) { "BUS"->"버스(${it.name})"; "SUBWAY"->"지하철(${it.name})"; else->"도보" }
                    }
                    logBuilder.append("   ㄴ 경로: $pathStr\n")
                }

                // 픽업 태스크 리스트 출력
                if (pickupTasks.isNotEmpty()) {
                    pickupTasks.forEach { task -> logBuilder.append("   ㄴ 🔔 $task\n") }
                } else {
                    logBuilder.append("   ㄴ (합류 가능한 팔로워 없음)\n")
                }
                logBuilder.append("\n")

                // 대장 경로 그리기 (전체)
                if (leader.mode == TravelMode.TRANSIT) {
                    visualizer.drawTransitRouteCut(rawTransitPaths[leader.id] ?: emptyList(), Int.MAX_VALUE, leader.color)
                } else {
                    visualizer.drawPolyline(leaderRoute.points, leader.color)
                }

                // -----------------------------------------------------
                // [로그 출력 & 그리기 - 팔로워]
                // -----------------------------------------------------
                for (member in group) {
                    if (member.id == leader.id) continue
                    val memberRoute = allRouteMap[member.id] ?: continue
                    val meetInfo = followerMeetInfos[member.id]

                    logBuilder.append("🏃 ${member.name} (팔로워)\n")
                    appendBasicInfo(logBuilder, member, memberRoute)

                    if (meetInfo != null) {
                        val (meetPoint, meetName) = meetInfo

                        // 1. 시간 계산 (합류 5분 전 도착 목표)
                        if (leaderStartTime != null) {
                            // 대장 도착 시간 계산
                            val lIdx = RouteMath.findNearestPathIndex(leaderRoute.points, meetPoint)
                            val lTime = RouteMath.estimateTimeFromStart(leaderRoute.points, lIdx, leaderRoute.distanceMeters, leaderRoute.sectionTimeSeconds)
                            val meetTime = (leaderStartTime.clone() as Calendar).apply { add(Calendar.SECOND, lTime) }

                            // 내 이동 시간 계산
                            val fIdx = RouteMath.findNearestPathIndex(memberRoute.points, meetPoint)
                            val fTime = RouteMath.estimateTimeFromStart(memberRoute.points, fIdx, memberRoute.distanceMeters, memberRoute.sectionTimeSeconds)

                            // 출발 시간 = 합류시간 - 이동시간 - 5분(Buffer)
                            val departTime = (meetTime.clone() as Calendar).apply {
                                add(Calendar.SECOND, -fTime)
                                add(Calendar.MINUTE, -5)
                            }
                            appendTimeLog(logBuilder, departTime, now, timeFormat)
                            logBuilder.append("   ㄴ 💡 합류 시간: ${timeFormat.format(meetTime.time)} 합류 예정 (5분 대기)\n")
                        }

                        // 2. 텍스트 수술 (경로 자르기)
                        if (member.mode == TravelMode.TRANSIT) {
                            val fullPath = rawTransitPaths[member.id] ?: emptyList()
                            val cutPathStr = generateCutPathString(fullPath, meetName)
                            logBuilder.append("   ㄴ 경로: $cutPathStr\n")
                        } else {
                            logBuilder.append("   ㄴ 경로: 도보 이동 > $meetName (합류)\n")
                        }

                        // 3. 지도 그리기 (경로 자르기 + 빨간선)
                        val cutIdx = RouteMath.findNearestPathIndex(memberRoute.points, meetPoint)
                        if (member.mode == TravelMode.TRANSIT) {
                            visualizer.drawTransitRouteCut(rawTransitPaths[member.id] ?: emptyList(), cutIdx, member.color)
                        } else {
                            if (cutIdx != -1) {
                                val cutPath = memberRoute.points.take(cutIdx + 1)
                                visualizer.drawPolyline(cutPath, member.color)
                            }
                        }

                        // 빨간 합류선 (Overlay)
                        val leaderCutIdx = RouteMath.findNearestPathIndex(leaderRoute.points, meetPoint)
                        if (leaderCutIdx != -1) {
                            visualizer.drawRedLine(leaderRoute.points.drop(leaderCutIdx))
                        }

                    } else {
                        // 합류 실패 시
                        logBuilder.append("   ㄴ (합류 실패: 각자 이동)\n")
                        if (targetTime != null) {
                            val departTime = (targetTime.clone() as Calendar).apply {
                                add(Calendar.SECOND, -memberRoute.sectionTimeSeconds)
                            }
                            appendTimeLog(logBuilder, departTime, now, timeFormat)
                        }
                        if (member.mode == TravelMode.TRANSIT) {
                            val fullStr = rawTransitPaths[member.id]?.joinToString(" > ") {
                                when (it.mode) { "BUS"->"버스(${it.name})"; "SUBWAY"->"지하철(${it.name})"; else->"도보" }
                            }
                            logBuilder.append("   ㄴ 경로: $fullStr\n")
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

    /** [Helper] 시간 로그 출력 (지각 여부 판별) */
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

    /** [Helper] 기본 정보(거리/시간/비용) 출력 */
    private fun appendBasicInfo(sb: StringBuilder, u: User, route: TransitPathSegment) {
        val distKm = route.distanceMeters / 1000.0
        val min = route.sectionTimeSeconds / 60
        val fare = if (route.totalFare > 0) " / ${route.totalFare}원" else ""
        sb.append("   ㄴ 정보: ${"%.1f".format(distKm)}km / ${min}분$fare\n")
    }

    /** [Helper] 혼자 이동 시 로그 출력 */
    private fun appendUserLog(sb: StringBuilder, u: User, route: TransitPathSegment, rawPaths: List<TransitPathSegment>?) {
        sb.append("${u.name} (${u.mode})\n")
        appendBasicInfo(sb, u, route)
        if (u.mode == TravelMode.TRANSIT && rawPaths != null) {
            val pathStr = rawPaths.joinToString(" > ") {
                when (it.mode) { "BUS"->"버스(${it.name})"; "SUBWAY"->"지하철(${it.name})"; else->"도보" }
            }
            sb.append("   ㄴ 경로: $pathStr\n")
        }
    }

    /** [Helper] 합류 지점 이후의 텍스트를 잘라내는 함수 */
    private fun generateCutPathString(segments: List<TransitPathSegment>, meetName: String): String {
        val sb = StringBuilder()
        var found = false
        for (seg in segments) {
            val modeStr = when (seg.mode) { "WALK"->"도보"; "BUS"->"버스(${seg.name})"; "SUBWAY"->"지하철(${seg.name})"; else->"" }
            // 이름 정규화 비교
            val normMeet = RouteMath.normalizeStationName(meetName)
            val hasStation = seg.stations.any { RouteMath.normalizeStationName(it.name) == normMeet }

            if (hasStation) {
                sb.append("$modeStr > $meetName (하차 후 합류!)")
                found = true
                break
            } else {
                sb.append("$modeStr > ")
            }
        }
        if (!found) return "경로 이동 > $meetName (여기서 합류!)"
        return sb.toString()
    }
}