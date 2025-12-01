package com.example.gachiga.util

import com.example.gachiga.data.Member
import com.kakao.vectormap.LatLng
import com.example.gachiga.data.StationPoint
import com.example.gachiga.data.TransitPathSegment
import com.example.gachiga.data.TravelMode
import kotlin.math.abs

/**
 * [경로 최적화 및 그룹핑 엔진]
 * 사용자의 경로 데이터를 분석하여 함께 이동할 수 있는 그룹을 형성하고,
 * 그룹 내의 리더(Leader)를 선출하며, 경로를 이어 붙이는(Stitching) 핵심 로직을 담당합니다.
 */
object RouteOptimizer {

    // [설정] 최대 대기 허용 시간 (초) -> 현재는 15분으로 설정되어 있으나,
    // 시간 검증 함수(checkTimeCompatibility)에서 이를 무시하고 항상 true를 반환하도록 되어 있음.
    // (이유: 도보 사용자가 일찍 출발해서 기다리는 시나리오를 지원하기 위함)
    private const val MAX_WAIT_SECONDS = 15 * 60

    // ========================================================================
    // [Section 1] 그룹핑 (Grouping Algorithm)
    // ========================================================================

    /**
     * 전체 유저를 분석하여 "함께 이동할 그룹 리스트"를 반환합니다.
     * @return List<List<User>> 형태 (예: [[A, B], [C], [D, E]])
     */
    fun findGroups(
        members: List<Member>,
        userRoutes: Map<Int, TransitPathSegment>
    ): List<List<Member>> {
        // 아직 그룹이 정해지지 않은 대기자 명단
        val unassigned = members.toMutableList()
        val groups = mutableListOf<List<Member>>()

        while (unassigned.isNotEmpty()) {
            // 1. 기준점(Pivot) 유저를 하나 꺼냅니다. (이 유저가 임시 대장이 됩니다)
            val pivot = unassigned.removeAt(0)
            val currentGroup = mutableListOf(pivot)
            val pivotRoute = userRoutes[pivot.id]

            // 경로 데이터가 없으면 혼자 그룹으로 처리
            if (pivotRoute == null) {
                groups.add(currentGroup)
                continue
            }

            // 2. 나머지 대기자들과 비교하여 합류 가능 여부를 판단합니다.
            val iterator = unassigned.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                val candidateRoute = userRoutes[candidate.id] ?: continue

                // -----------------------------------------------------------
                // [Step 1] 공간적 매칭 확인 (Spatial Check)
                // - 두 사람의 경로가 지리적으로 만나는 지점이 있는가?
                // -----------------------------------------------------------
                var meetPoint: LatLng? = null

                // 1) 이름 매칭: 환승역 이름이 같은지 확인 (가장 정확함)
                val commonStation = RouteMath.findCommonStation(pivotRoute.stations, candidateRoute.stations)
                if (commonStation != null) {
                    meetPoint = LatLng.from(commonStation.lat, commonStation.lon)
                }

                // 2) 정류장 근접 매칭: 이름이 달라도 정류장이 상대방 경로 근처에 있는지 확인
                if (meetPoint == null) {
                    // (A) 차 vs 대중교통
                    if (pivot.mode == TravelMode.CAR && candidate.mode == TravelMode.TRANSIT) {
                        meetPoint = findProximityPoint(candidateRoute.stations, pivotRoute.points)
                    } else if (pivot.mode == TravelMode.TRANSIT && candidate.mode == TravelMode.CAR) {
                        meetPoint = findProximityPoint(pivotRoute.stations, candidateRoute.points)
                    }
                    // (B) 차 vs 도보 (도보 경로상의 점들을 정류장처럼 취급하여 검사)
                    else if (pivot.mode == TravelMode.CAR && candidate.mode == TravelMode.WALK) {
                        meetPoint = findProximityPointFromPath(candidateRoute.points, pivotRoute.points)
                    } else if (pivot.mode == TravelMode.WALK && candidate.mode == TravelMode.CAR) {
                        meetPoint = findProximityPointFromPath(pivotRoute.points, candidateRoute.points)
                    }
                }

                // 3) 좌표 매칭: 도로(LineString) 자체가 겹치는지 확인 (자동차 동행 등)
                if (meetPoint == null) {
                    val sharedSegments = RouteMath.findAllSharedSegments(listOf(pivotRoute.points, candidateRoute.points))
                    if (sharedSegments.isNotEmpty()) {
                        meetPoint = sharedSegments.first().first()
                    }
                }

                // -----------------------------------------------------------
                // [Step 2] 유효성 검증 (Validation)
                // - 만난다고 해서 무조건 태우면 안 됨 (납치 방지, 시간 검증)
                // -----------------------------------------------------------
                if (meetPoint != null) {
                    // A. [납치 방지] 목적지 코앞(500m)에서 만나는 건 무효
                    // 이유: 도착지 입구에서 경로가 겹치는 것을 '카풀 합류'로 오판하는 버그 방지
                    val destination = pivotRoute.points.last()
                    val distToDest = RouteMath.haversineMeters(meetPoint, destination)

                    if (distToDest > 500) {
                        // B. [시간 검증] 시간적으로 만날 수 있는가?
                        // (현재는 항상 true를 반환하여, '일찍 도착해서 대기하는 시나리오'를 허용함)
                        if (checkTimeCompatibility(pivotRoute, candidateRoute, meetPoint)) {
                            // 모든 조건 통과 -> 같은 팀 확정!
                            currentGroup.add(candidate)
                            iterator.remove() // 대기 명단에서 제거
                        }
                    }
                }
            }
            // 한 그룹 완성
            groups.add(currentGroup)
        }
        return groups
    }

    // ========================================================================
    // [Section 2] Helper Functions (매칭 세부 로직)
    // ========================================================================

    /**
     * 정류장 리스트 중 자동차 경로(path)와 50m 이내로 가까운 첫 번째 지점을 찾습니다.
     */
    private fun findProximityPoint(stations: List<StationPoint>, path: List<LatLng>): LatLng? {
        val nearStation = stations.find { station ->
            RouteMath.isStationNearPath(station, path, 50.0)
        }
        return nearStation?.let { LatLng.from(it.lat, it.lon) }
    }

    /**
     * 도보 경로(점 리스트) 중 자동차 경로와 가까운 지점을 찾습니다.
     * 성능 최적화를 위해 점을 5개씩 건너뛰며(Step 5) 검사합니다.
     */
    private fun findProximityPointFromPath(walkerPath: List<LatLng>, carPath: List<LatLng>): LatLng? {
        for (i in walkerPath.indices step 5) {
            val point = walkerPath[i]
            val tempStation = StationPoint("Check", point.latitude, point.longitude)
            if (RouteMath.isStationNearPath(tempStation, carPath, 50.0)) {
                return point
            }
        }
        return null
    }

    /**
     * [시간 검증 로직]
     * 두 사용자가 합류 지점에서 시간적으로 만날 수 있는지 확인합니다.
     * @return 현재는 항상 true 반환 (도보 사용자의 선착 대기 허용을 위해 제한 해제)
     */
    private fun checkTimeCompatibility(
        routeA: TransitPathSegment,
        routeB: TransitPathSegment,
        meetPoint: LatLng
    ): Boolean {
        // [수정 사항] 원래는 도착 예상 시간 차이가 15분 이내여야 true를 반환했으나,
        // 현재는 "느린 사람이 먼저 가서 기다린다"는 시나리오를 지원하기 위해 검사를 생략합니다.
        // 실제 '지각 여부'는 UI 레벨(RouteLogicManager)에서 빨간 글씨로 안내합니다.
        return true
    }

    // ========================================================================
    // [Section 3] 대장 선정 및 경로 수술 (Post-Grouping)
    // ========================================================================

    /**
     * 그룹 내에서 대장(Leader)을 선정합니다.
     * 1순위: 자동차(CAR) 유저
     * 2순위: 도보 이동 거리가 가장 짧은 유저 (대중교통끼리의 경우)
     */
    fun decideLeader(
        group: List<Member>,
        userRoutes: Map<Int, TransitPathSegment>
    ): Member? {
        if (group.isEmpty()) return null
        // 1. 자동차 우선
        val carUser = group.find { it.mode == TravelMode.CAR }
        if (carUser != null) return carUser

        // 2. 덜 걷는 사람 우선 (가장 편한 경로를 따름)
        return group.filter { userRoutes.containsKey(it.id) }
            .minByOrNull { member ->
                userRoutes[member.id]!!.distanceMeters
            }
    }

    /**
     * [경로 잇기 (Stitching)] (Visualizer에서 사용 안 함, 참고용 로직)
     * 팔로워의 경로를 자르고 대장의 경로를 이어 붙인 새로운 좌표 리스트를 생성합니다.
     * (현재는 Visualizer에서 별도로 drawRedLine을 호출하므로 이 함수는 사용되지 않을 수 있습니다.)
     */
    fun stitchRoutes(
        followerRoute: TransitPathSegment,
        leaderRoute: TransitPathSegment
    ): List<LatLng> {
        // 합류 지점 재탐색 (표시용이 아닌 계산용)
        var meetPoint: LatLng? = null
        val commonStation = RouteMath.findCommonStation(followerRoute.stations, leaderRoute.stations)

        if (commonStation != null) {
            meetPoint = LatLng.from(commonStation.lat, commonStation.lon)
        } else {
            val shared = RouteMath.findAllSharedSegments(listOf(followerRoute.points, leaderRoute.points))
            if (shared.isNotEmpty()) {
                meetPoint = shared.first().first()
            }
        }

        // 근접 매칭 등 예외 처리 (단순화된 로직)
        if (meetPoint == null) {
            val fIdx = RouteMath.findNearestPathIndex(followerRoute.points, leaderRoute.points.first())
            if (fIdx != -1) meetPoint = followerRoute.points[fIdx]
        }

        if (meetPoint == null) return followerRoute.points

        // 자르고 붙이기 (Cut & Stitch)
        val cutIndexFollower = findNearestPointIndex(followerRoute.points, meetPoint)
        val followerPart = followerRoute.points.take(cutIndexFollower + 1)

        val cutIndexLeader = findNearestPointIndex(leaderRoute.points, meetPoint)
        val leaderPart = leaderRoute.points.drop(cutIndexLeader)

        return followerPart + leaderPart
    }

    // 내부용 인덱스 찾기 함수 (RouteMath에 있는 것과 동일, 독립성 보장을 위해 포함)
    private fun findNearestPointIndex(points: List<LatLng>, target: LatLng): Int {
        if (points.isEmpty()) return 0
        var minIdx = 0
        var minDst = Double.MAX_VALUE
        points.forEachIndexed { index, p ->
            val dist = RouteMath.haversineMeters(p, target)
            if (dist < minDst) {
                minDst = dist
                minIdx = index
            }
        }
        return minIdx
    }
}