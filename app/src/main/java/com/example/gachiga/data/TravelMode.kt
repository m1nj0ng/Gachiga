package com.example.gachiga.data

/**
 * 이동 수단 (자동차, 대중교통, 도보)
 * 그룹핑 및 대장 선정 시 우선순위 판단 기준으로 사용됩니다.
 */
enum class TravelMode {
    CAR,     // 자동차 (대장 선정 1순위)
    TRANSIT, // 대중교통 (버스, 지하철)
    WALK     // 도보
}