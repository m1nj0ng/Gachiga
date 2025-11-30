package com.miujong.gachiga10.data.model

import com.kakao.vectormap.LatLng

/**
 * [자동차 경로 결과]
 * TMAP 자동차 API 응답의 핵심 데이터입니다.
 */
data class TmapCarRoute(
    val points: List<LatLng>, // 전체 경로 좌표 (그리기용)
    val km: Double,           // 총 거리
    val minutes: Int,         // 총 소요 시간
    val toll: Int             // 톨게이트 비용
)

/**
 * [대중교통 옵션 정보]
 * 화면에 "45분 소요 / 1250원" 처럼 요약 정보를 보여줄 때 사용합니다.
 */
data class TransitOption(
    val title: String,        // 경로 요약 (예: 지하철+버스)
    val minutes: Int,         // 총 소요 시간
    val distanceKm: Double,   // 총 거리
    val fare: Int,            // 총 요금
    val path: List<TransitPathSegment>? = null // 상세 경로 (구간별 정보)
)

/**
 * [정류장 정보]
 * 합류 지점 계산 시 단순 이름 비교뿐만 아니라 좌표 기반 근접 매칭에 사용됩니다.
 */
data class StationPoint(
    val name: String,
    val lat: Double,
    val lon: Double
)

/**
 * [통합 경로 세그먼트]
 * 자동차, 대중교통, 도보 경로를 모두 수용하는 공통 데이터 구조입니다.
 * Optimizer와 Visualizer는 이 객체를 사용하여 계산하고 그립니다.
 */
data class TransitPathSegment(
    val points: List<LatLng>,   // 구간 좌표 리스트
    val mode: String,           // 이동 모드 (CAR, BUS, SUBWAY, WALK)
    val color: String?,         // 노선 색상 (대중교통용)
    val name: String?,          // 노선명 (150번, 2호선 등)

    /** 경유하는 모든 정류장 리스트 (합류 지점 탐색용) */
    val stations: List<StationPoint> = emptyList(),

    val distanceMeters: Int = 0,     // 구간 거리 (시간 추정용)
    val sectionTimeSeconds: Int = 0, // 구간 소요 시간 (스케줄링용)
    val totalFare: Int = 0           // 구간 요금
)