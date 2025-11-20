package com.miujong.gachiga10.data.model

import com.kakao.vectormap.LatLng

// 자동차 경로 결과
data class TmapCarRoute(
    val points: List<LatLng>,
    val km: Double,
    val minutes: Int,
    val toll: Int
)

// 대중교통 옵션 결과 (화면 표시용)
data class TransitOption(
    val title: String,
    val minutes: Int,
    val distanceKm: Double,
    val fare: Int,
    val path: List<TransitPathSegment>? = null
)

// 대중교통 상세 경로 (지도 그리기용)
data class TransitPathSegment(
    val points: List<LatLng>,
    val mode: String,
    val color: String?,
    val name: String?
)