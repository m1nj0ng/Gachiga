package com.example.gachiga.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * [TMAP 자동차 경로 API]
 * - 역할: 자동차 이동 시 경로 좌표, 소요 시간, 거리, 요금 정보 요청
 * - 특징: 응답이 GeoJSON 표준 형식을 따르므로 Geometry 파싱 구조가 독특합니다.
 */
interface TmapRouteApiService {

    @GET("tmap/routes")
    suspend fun getCarRoute(
        @Query("startX") startX: Double,
        @Query("startY") startY: Double,
        @Query("endX")   endX: Double,
        @Query("endY")   endY: Double,
        @Query("searchOption") searchOption: Int,
        @Query("version") version: Int = 1,
        @Query("format")  format: String = "json"
    ): TmapRouteResponse
}

// ------------------------------------------------------------
// [응답 데이터 모델 - GeoJSON 구조]
// ------------------------------------------------------------

/**
 * [최상위 응답 객체]
 * TMAP API는 GeoJSON의 'FeatureCollection' 구조를 따릅니다.
 * 'features' 리스트 안에 경로를 구성하는 점(Point)과 선(LineString)들이 들어있습니다.
 */
data class TmapRouteResponse(
    @SerializedName("type") val type: String?,
    @SerializedName("features") val features: List<TmapFeature>?
)

/**
 * [Feature 객체]
 * 지도상의 하나의 요소(점 또는 선)를 나타냅니다.
 * - geometry: 실제 좌표 데이터 (위도/경도)
 * - properties: 그 구간의 속성 정보 (거리, 시간, 도로명 등)
 */
data class TmapFeature(
    @SerializedName("type") val type: String?,
    @SerializedName("geometry") val geometry: TmapGeometry?,
    @SerializedName("properties") val properties: TmapProperties?
)

/**
 * [속성 정보 객체]
 * 경로의 총 거리, 총 시간, 요금 등 사용자가 실제로 궁금해하는 정보가 담겨 있습니다.
 */
data class TmapProperties(
    @SerializedName("totalDistance") val totalDistance: Int?, // 총 거리 (단위: m)
    @SerializedName("totalTime")     val totalTime: Int?,     // 총 소요 시간 (단위: 초)
    @SerializedName("index")        val index: Int?,         // 경로 순서 인덱스
    @SerializedName("name")         val name: String?,        // 도로명 또는 안내 문구
    @SerializedName("tollFare")      val tollFare: Int?,      // 톨게이트 비용
    @SerializedName("totalFare")     val totalFare: Int?,     // 총 요금 (주유비 제외)
    @SerializedName("taxiFare")      val taxiFare: Int?       // 택시비 예상액
)