package com.miujong.gachiga10.data.model

/**
 * 개별 사용자 정보 데이터
 * UI 표시 및 경로 탐색에 필요한 필수 정보를 담고 있습니다.
 */
data class User(
    val id: Int,                // 고유 ID
    var name: String,           // 표시 이름 (예: 사용자 1)
    var x: Double? = null,      // 출발지 경도
    var y: Double? = null,      // 출발지 위도
    var placeName: String = "", // 출발지 명칭
    val color: Int,             // 지도 경로 색상 (ARGB)
    var mode: TravelMode = TravelMode.CAR, // 이동 수단

    /**
     * 경로 탐색 옵션
     * - Car: 0(추천), 1(무료), 2(최소시간) ...
     * - Transit: 0(최적), 1(최소환승) ...
     */
    var searchOption: Int = 0
)