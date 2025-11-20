package com.miujong.gachiga10.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/* Tmap 대중교통 경로 검색 API
 *
 * baseUrl : https://apis.openapi.sk.com/
 * path    : POST /transit/routes
 *
 * 헤더 : appKey: {발급받은 키}
 * 바디 : startX, startY, endX, endY, count, format 등
 * 응답 : metaData.plan.itineraries[*] ...
 */

interface TransitApiService {
    @POST("transit/routes")
    suspend fun getTransitRoutes(
        @Body body: TransitRouteRequest
    ): TransitRouteResponse
}

/* -------------------- Retrofit -------------------- */

object TransitRetrofit {
    private const val BASE_URL = "https://apis.openapi.sk.com/"

    fun create(): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}

/* -------------------- Request / Response 모델 -------------------- */

/** 대중교통 경로 요청 */
data class TransitRouteRequest(
    @SerializedName("startX") val startX: String,
    @SerializedName("startY") val startY: String,
    @SerializedName("endX")   val endX: String,
    @SerializedName("endY")   val endY: String,
    @SerializedName("count")  val count: Int = 1,       // 요청할 경로 개수
    @SerializedName("format") val format: String = "json"
)

/** 최상위 응답 */
data class TransitRouteResponse(
    @SerializedName("metaData") val metaData: TransitMetaData?
)

data class TransitMetaData(
    @SerializedName("plan") val plan: TransitPlan?
)

data class TransitPlan(
    @SerializedName("itineraries") val itineraries: List<TransitItinerary>?
)

/** 한 개의 대중교통 경로(환승 포함) */
data class TransitItinerary(
    @SerializedName("totalTime")     val totalTime: Int?,      // seconds
    @SerializedName("totalDistance") val totalDistance: Int?,  // meters
    @SerializedName("fare")         val fare: TransitFare?,   // 전체 요금 블록
    @SerializedName("legs")         val legs: List<TransitLeg>?
)

/** 구간(버스 / 지하철 / 도보 등) */
data class TransitLeg(
    @SerializedName("mode")        val mode: String?,         // WALK / BUS / SUBWAY ...
    @SerializedName("sectionTime") val sectionTime: Int?,     // 소요시간(초)
    @SerializedName("distance")    val distance: Int?,        // 거리(m)
    @SerializedName("routeColor")  val routeColor: String?,   // [추가] 노선 색상 (예: "#33CC99")
    @SerializedName("passShape")   val passShape: Any?,       // [추가] 경로 좌표 문자열 ("127.1,37.5 127.2,37.6 ...") // [수정] String -> Any?
    @SerializedName("route")       val route: String?         // [추가] 노선 명칭 (예: "2호선", "143번", "경부고속철도")
)

/** 요금 정보 */
data class TransitFare(
    @SerializedName("regular") val regular: TransitRegularFare?
)

data class TransitRegularFare(
    @SerializedName("totalFare") val totalFare: Int?          // 기본요금 (원)
)