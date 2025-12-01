package com.example.gachiga.util

import com.kakao.vectormap.LatLng
import com.google.gson.internal.LinkedTreeMap
import com.example.gachiga.data.StationPoint
import java.lang.Math.toRadians
import kotlin.math.*

/**
 * [경로 계산 유틸리티]
 * 경로 매칭, 거리 계산, 좌표 변환, 시간 추정 등 순수 수학적인 계산을 담당하는 객체입니다.
 * Android Context나 UI에 의존하지 않는 순수 함수(Pure Functions)들로 구성되어 있습니다.
 */
object RouteMath {

    // =========================================================================
    // [Section 1] 정류장 이름 정규화 및 매칭 로직
    // =========================================================================

    /**
     * 정류장 이름 정규화 함수
     * 문자열 비교 시 불일치를 줄이기 위해 괄호, '역' 접미사, 공백 등을 제거합니다.
     * 예: "서울역(1호선)" -> "서울", "강남역" -> "강남"
     */
    fun normalizeStationName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("\\(.*?\\)"), "") // 괄호 및 내부 내용 제거
            .replace("역", "")               // '역' 글자 제거
            .replace(" ", "")                // 공백 제거
            .trim()
    }

    /**
     * [이름 기반 매칭] 두 경로의 정류장 리스트를 비교하여 공통된 지점을 찾습니다.
     * 대중교통 간 환승 지점(예: 지하철 환승역)을 찾을 때 주로 사용됩니다.
     * @return 매칭된 B경로의 StationPoint 객체 (좌표 포함), 없으면 null
     */
    fun findCommonStation(listA: List<StationPoint>, listB: List<StationPoint>): StationPoint? {
        // A 경로의 역 이름들을 정규화하여 Set으로 변환 (검색 속도 최적화 O(1))
        val setA = listA.map { normalizeStationName(it.name) }.filter { it.isNotEmpty() }.toSet()

        // B 경로를 순회하며 A 집합에 존재하는지 확인
        for (stationB in listB) {
            val normB = normalizeStationName(stationB.name)
            if (normB.isNotEmpty() && normB in setA) {
                return stationB // 매칭 성공
            }
        }
        return null
    }

    /**
     * [거리 기반 근접 판정] 특정 정류장(점)이 자동차 경로(선)의 반경 내에 존재하는지 검사합니다.
     * 이름이 달라도 위치가 같거나, 경로 상을 스쳐 지나가는 경우(Pick-up point)를 판단합니다.
     *
     * @param station 대중교통/도보 이용자의 특정 지점 (정류장 or 좌표)
     * @param path 자동차 이동 경로 (좌표 리스트)
     * @param radiusMeters 감지 반경 (기본 100m: GPS 오차 및 도로 폭 고려)
     */
    fun isStationNearPath(station: StationPoint, path: List<LatLng>, radiusMeters: Double = 100.0): Boolean {
        if (station.lat == 0.0 || station.lon == 0.0) return false
        val stationLatLng = LatLng.from(station.lat, station.lon)

        // 경로상의 모든 점(Point)과 정류장 간의 거리를 계산 (Brute-force)
        // 모바일 환경에서 수천 개의 점을 단순 거리 계산하는 것은 성능상 문제가 없음
        for (point in path) {
            if (haversineMeters(stationLatLng, point) <= radiusMeters) {
                return true
            }
        }
        return false
    }

    // =========================================================================
    // [Section 2] 시간 추정 및 인덱싱 (Interpolation)
    // =========================================================================

    /**
     * [시간 추정] 경로상의 특정 지점(인덱스)까지 도달하는 데 걸리는 시간을 계산합니다.
     * API가 구간별 상세 시간을 제공하지 않으므로, '거리 비례 추정(Linear Interpolation)' 방식을 사용합니다.
     * (전제: 이동 속도는 구간 내에서 일정하다고 가정)
     *
     * @param path 전체 경로 점 리스트
     * @param targetIndex 목표 지점의 인덱스 (findNearestPathIndex로 구함)
     * @param totalDistMeters API가 제공한 전체 경로 거리 (m)
     * @param totalTimeSec API가 제공한 전체 소요 시간 (초)
     * @return 출발 후 해당 지점까지 흐른 시간 (초)
     */
    fun estimateTimeFromStart(
        path: List<LatLng>,
        targetIndex: Int,
        totalDistMeters: Int,
        totalTimeSec: Int
    ): Int {
        // 예외 처리: 데이터가 없거나 유효하지 않은 경우
        if (path.isEmpty() || targetIndex <= 0 || totalDistMeters == 0) return 0
        if (targetIndex >= path.lastIndex) return totalTimeSec

        // 1. 시작점부터 targetIndex까지 실제 경로를 따라가며 누적 거리(Partial Distance) 계산
        var partialDist = 0.0
        for (i in 0 until targetIndex) {
            partialDist += haversineMeters(path[i], path[i + 1])
        }

        // 2. 전체 거리 대비 비율 계산 (0.0 ~ 1.0)
        val ratio = (partialDist / totalDistMeters.toDouble()).coerceIn(0.0, 1.0)

        // 3. 전체 시간에 비율을 곱하여 소요 시간 추정
        return (totalTimeSec * ratio).toInt()
    }

    /**
     * [인덱스 검색] 특정 좌표(합류점)가 경로 리스트의 몇 번째 점인지 찾습니다.
     * 가장 가까운 점(Nearest Neighbor)의 인덱스를 반환합니다.
     * 경로 자르기(Cut)나 시간 추정 시 기준점이 됩니다.
     */
    fun findNearestPathIndex(path: List<LatLng>, target: LatLng): Int {
        if (path.isEmpty()) return -1

        var minIdx = -1
        var minDst = Double.MAX_VALUE

        // 전체 경로를 탐색하며 거리가 가장 가까운 점을 찾음
        path.forEachIndexed { index, p ->
            val dist = haversineMeters(p, target)
            if (dist < minDst) {
                minDst = dist
                minIdx = index
            }
        }
        return minIdx
    }

    // =========================================================================
    // [Section 3] 기하학적 경로 겹침 분석 (Parallel Segments)
    // =========================================================================

    /**
     * 여러 사용자 경로들 간에 기하학적으로 겹치는(동행하는) 모든 구간을 찾아냅니다.
     * @return 겹치는 구간들의 좌표 리스트 목록
     */
    fun findAllSharedSegments(routes: List<List<LatLng>>): List<List<LatLng>> {
        val results = mutableListOf<List<LatLng>>()
        // 이중 루프로 모든 경로 쌍을 비교
        for (i in 0 until routes.size - 1) {
            for (j in i + 1 until routes.size) {
                results.addAll(findSharedSegments(routes[i], routes[j]))
            }
        }
        return results
    }

    /**
     * 두 경로(A, B)가 일정 거리 이상 "나란히(Parallel)" 달리는 구간을 찾습니다.
     * 단순 교차(Cross)가 아니라 실제로 도로를 공유하며 동행하는지 판단합니다.
     *
     * @param tolMeters 허용 거리 오차 (예: 30m 이내면 같은 도로로 간주)
     * @param angleTol 허용 각도 오차 (예: 45도 이내면 같은 방향으로 간주)
     */
    private fun findSharedSegments(
        a: List<LatLng>, b: List<LatLng>,
        tolMeters: Double = 30.0, angleTol: Double = 45.0
    ): List<List<LatLng>> {
        val out = mutableListOf<List<LatLng>>()
        var i = 0

        fun dist(p1: LatLng, p2: LatLng) = haversineMeters(p1, p2)

        while (i < a.size) {
            // A[i]와 가까운 B의 점(nearIdx) 찾기
            val nearIdx = b.indexOfFirst { dist(a[i], it) <= tolMeters }
            if (nearIdx == -1) { i++; continue }

            val segment = mutableListOf(a[i])
            var ia = i
            var jb = nearIdx

            // 연속성 검사: 두 경로가 계속해서 같이 가는지 확인
            while (ia + 1 < a.size) {
                val nextA = a[ia + 1]
                // B 경로 탐색 범위 제한 (너무 멀리 점프하는 오매칭 방지)
                val searchRange = (jb + 1)..min(b.lastIndex, jb + 5)
                val bestJ = searchRange.minByOrNull { dist(nextA, b[it]) } ?: break

                // 1. 거리 이탈 검사
                if (dist(nextA, b[bestJ]) > tolMeters) break

                // 2. 각도 이탈 검사 (진행 방향이 다르면 갈라지는 길로 판단)
                val angA = bearingDeg(segment.last(), nextA)
                val angB = bearingDeg(b[jb], b[bestJ])
                if (angleDiffDeg(angA, angB) > angleTol) break

                segment.add(nextA)
                ia++
                jb = bestJ
            }

            // 의미 있는 길이(점 6개 이상)만 유효한 합류 구간으로 인정
            if (segment.size >= 6) out.add(segment)
            i = ia + 1
        }
        return out
    }

    // =========================================================================
    // [Section 4] 기본 수학 유틸리티 (거리, 각도, 파싱)
    // =========================================================================

    /**
     * TMAP API의 'passShape' 문자열 데이터를 좌표 리스트로 파싱합니다.
     * "lon,lat lon,lat ..." 공백 구분 문자열 형식 처리
     */
    fun parsePassShape(passShape: Any?): List<LatLng> {
        val shapeString = when (passShape) {
            is String -> passShape
            is LinkedTreeMap<*, *> -> passShape["linestring"] as? String
            else -> null
        }
        if (shapeString.isNullOrBlank()) return emptyList()

        return try {
            shapeString.trim().split(Regex("\\s+")).mapNotNull { coords ->
                val split = coords.split(",")
                if (split.size == 2) {
                    val lon = split[0].trim().toDoubleOrNull() ?: 0.0
                    val lat = split[1].trim().toDoubleOrNull() ?: 0.0
                    if (lon != 0.0 && lat != 0.0) LatLng.from(lat, lon) else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * [하버사인 공식 (Haversine Formula)]
     * 지구 곡면을 고려하여 두 좌표 간의 최단 직선 거리(미터)를 계산합니다.
     */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0 // 지구 반지름 (m)
        val lat1 = toRadians(a.latitude); val lat2 = toRadians(b.latitude)
        val dLat = toRadians(b.latitude - a.latitude)
        val dLon = toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    /** 두 좌표 사이의 방위각(Bearing) 계산 (0~360도) */
    private fun bearingDeg(p: LatLng, q: LatLng): Double {
        val lat1 = toRadians(p.latitude); val lat2 = toRadians(q.latitude)
        val dLon = toRadians(q.longitude - p.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 두 각도의 최소 차이 계산 (180도 초과 시 보정) */
    private fun angleDiffDeg(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180) 360 - diff else diff
    }
}