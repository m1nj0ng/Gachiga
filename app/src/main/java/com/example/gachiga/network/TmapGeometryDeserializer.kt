package com.example.gachiga.network

import com.google.gson.*
import java.lang.reflect.Type

/**
 * [TMAP Geometry 커스텀 해석기]
 * - 역할: JSON 응답의 "type" 필드 값("LineString" vs "MultiLineString")을 보고,
 * "coordinates" 배열을 알맞은 데이터 클래스(LineString/MultiLineString)로 변환합니다.
 * - 필요성: 기본 Gson은 하나의 필드에 여러 타입이 들어오는 것을 자동으로 처리하지 못하므로, 수동 파싱 로직이 필수적입니다.
 */
class TmapGeometryDeserializer : JsonDeserializer<TmapGeometry> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): TmapGeometry {

        val obj = json.asJsonObject
        // 1. 지형 타입 확인 (LineString 인지 MultiLineString 인지)
        val type = obj["type"]?.asString ?: ""

        // 2. 타입에 따라 좌표 배열 파싱 분기
        return when (type) {
            "LineString" -> {
                // [ [lon, lat], ... ] 구조
                val arr = obj["coordinates"].asJsonArray
                TmapGeometry.LineString(parseCoords(arr))
            }

            "MultiLineString" -> {
                // [ [ [lon, lat], ... ], ... ] 구조 (배열 안에 배열)
                val outer = obj["coordinates"].asJsonArray
                val lines = outer.map { part ->
                    parseCoords(part.asJsonArray)
                }
                TmapGeometry.MultiLineString(lines)
            }

            else -> {
                // 알 수 없는 타입이거나 Point 타입인 경우 빈 리스트 반환 (안전장치)
                TmapGeometry.LineString(emptyList())
            }
        }
    }

    /**
     * [Helper] 좌표 배열 파싱 함수
     * JSON Array [lon, lat] -> Coord 객체로 변환
     */
    private fun parseCoords(arr: JsonArray): List<TmapGeometry.Coord> =
        arr.mapNotNull { item ->
            val pair = item.asJsonArray
            if (pair.size() >= 2) {
                val lon = pair[0].asDouble
                val lat = pair[1].asDouble
                TmapGeometry.Coord(lon, lat)
            } else null
        }
}