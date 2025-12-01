package com.example.gachiga.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * [TMAP 지형 데이터 공통 타입] (Sealed Class)
 * TMAP API의 'geometry' 필드는 'LineString'(선)일 수도 있고, 'MultiLineString'(여러 선)일 수도 있습니다.
 * 이 다형성(Polymorphism)을 처리하기 위해 Sealed Class로 묶어서 관리합니다.
 */
sealed class TmapGeometry {

    /**
     * [단일 경로선 (LineString)]
     * - 구조: "coordinates": [ [lon, lat], [lon, lat], ... ]
     * - 대부분의 자동차 경로는 이 형태로 옵니다.
     */
    data class LineString(
        val coords: List<Coord>
    ) : TmapGeometry()

    /**
     * [복합 경로선 (MultiLineString)]
     * - 구조: "coordinates": [ [ [lon, lat], ... ], [ [lon, lat], ... ] ]
     * - 경로가 중간에 끊기거나 복잡한 교차로일 때, 여러 개의 선 뭉치로 옵니다.
     */
    data class MultiLineString(
        val multiple: List<List<Coord>>
    ) : TmapGeometry()

    /**
     * [좌표 객체]
     * TMAP은 [경도(lon), 위도(lat)] 순서의 배열로 좌표를 줍니다.
     */
    data class Coord(
        val lon: Double,
        val lat: Double
    )
}