package com.miujong.gachiga10.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * [TMAP 대중교통 API]
 * - 역할: 버스, 지하철, 도보가 섞인 복합 경로 탐색
 * - 특징: GET이 아닌 POST 방식을 사용하며, JSON Body에 요청 정보를 담아 보냅니다.
 */
interface TransitApiService {
    @POST("transit/routes")
    suspend fun getTransitRoutes(
        @Body body: TransitRouteRequest
    ): TransitRouteResponse
}

/* -------------------- Request / Response 모델 -------------------- */

/**
 * [대중교통 요청 바디]
 * API 요청 시 Body에 담아서 보낼 검색 조건들입니다.
 */
data class TransitRouteRequest(
    @SerializedName("startX") val startX: String,
    @SerializedName("startY") val startY: String,
    @SerializedName("endX")   val endX: String,
    @SerializedName("endY")   val endY: String,
    @SerializedName("count")  val count: Int = 1, // 반환받을 경로의 개수 (1개면 최적경로만)
    @SerializedName("format") val format: String = "json"
)

/**
 * [대중교통 응답 최상위 래퍼]
 * 메타데이터 안에 실제 계획(Plan)이 들어있는 구조입니다.
 */
data class TransitRouteResponse(
    @SerializedName("metaData") val metaData: TransitMetaData?
)

data class TransitMetaData(
    @SerializedName("plan") val plan: TransitPlan?
)

/**
 * [경로 계획 객체]
 * 여러 개의 추천 경로(itineraries) 리스트를 포함합니다.
 */
data class TransitPlan(
    @SerializedName("itineraries") val itineraries: List<TransitItinerary>?
)

/**
 * [단일 경로 객체 (Itinerary)]
 * 사용자가 선택할 수 있는 하나의 경로 옵션입니다. (예: 1호선 -> 150번 버스)
 * 전체 시간, 거리, 요금 정보와 세부 구간(legs) 정보를 가집니다.
 */
data class TransitItinerary(
    @SerializedName("totalTime")     val totalTime: Int?,     // 총 소요 시간 (초)
    @SerializedName("totalDistance") val totalDistance: Int?, // 총 거리 (m)
    @SerializedName("fare")         val fare: TransitFare?,   // 요금 정보 객체
    @SerializedName("legs")         val legs: List<TransitLeg>? // 상세 이동 구간 리스트
)

/**
 * [이동 구간 객체 (Leg)]
 * 경로를 구성하는 각각의 이동 단위입니다. (예: 도보 구간, 지하철 탑승 구간)
 * 합류 지점 계산 시 가장 중요한 단위가 됩니다.
 */
data class TransitLeg(
    @SerializedName("mode")        val mode: String?,        // 이동 수단 (WALK, BUS, SUBWAY)
    @SerializedName("sectionTime") val sectionTime: Int?,    // 구간 소요 시간
    @SerializedName("distance")    val distance: Int?,       // 구간 거리
    @SerializedName("routeColor")  val routeColor: String?,  // 노선 색상 (예: 2호선 초록색)
    @SerializedName("passShape")   val passShape: Any?,      // 경로 형상 (지도 그리기용 좌표)
    @SerializedName("route")       val route: String?,       // 노선명 (예: 150번, 2호선)
    @SerializedName("startName")   val startName: String?,   // 승차역/출발지 이름
    @SerializedName("endName")     val endName: String?,     // 하차역/도착지 이름
    @SerializedName("passStopList") val passStopList: TransitPassStopList? // 경유 정류장 리스트
)

data class TransitFare(
    @SerializedName("regular") val regular: TransitRegularFare?
)

data class TransitRegularFare(
    @SerializedName("totalFare") val totalFare: Int?
)

/**
 * [정류장 리스트 래퍼]
 * 주의: API 응답 JSON의 키값이 "stations"로 되어 있어 매핑에 주의해야 합니다.
 */
data class TransitPassStopList(
    @SerializedName("stations") val stationList: List<TransitStation>?
)

/**
 * [개별 정류장 정보]
 * 구간 내에서 거쳐가는 정류장의 상세 정보입니다.
 */
data class TransitStation(
    @SerializedName("index")       val index: Int?,
    @SerializedName("stationName") val stationName: String?, // 정류장 이름 (합류 판단 1순위)
    @SerializedName("lon")         val lon: String?,         // 경도 (근접 판단 2순위)
    @SerializedName("lat")         val lat: String?          // 위도
)