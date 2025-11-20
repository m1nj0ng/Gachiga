package com.miujong.gachiga10.network

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/** 최상위 응답 */
data class TmapRouteResponse(
    @SerializedName("type") val type: String?,
    @SerializedName("features") val features: List<TmapFeature>?
)

/** 한 개의 Feature (geometry + 속성) */
data class TmapFeature(
    @SerializedName("type") val type: String?,
    @SerializedName("geometry") val geometry: TmapGeometry?,
    @SerializedName("properties") val properties: TmapProperties?
)

/** 경로 요약/세부 정보 */
data class TmapProperties(
    @SerializedName("totalDistance") val totalDistance: Int?, // m
    @SerializedName("totalTime")     val totalTime: Int?,     // sec
    @SerializedName("index")        val index: Int?,         // 구간 인덱스
    @SerializedName("name")         val name: String?,        // 도로명 등
    @SerializedName("tollFare")      val tollFare: Int?,      // 톨게이트 비용
    @SerializedName("totalFare")     val totalFare: Int?,     // 전체 요금(톨 포함)
    @SerializedName("taxiFare")      val taxiFare: Int?       // 택시요금 (옵션)
)

/** 실제 API 인터페이스 (자동차 기준) */
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

/* ───────────────── Retrofit Factory ───────────────── */

object TmapRouteRetrofit {
    private const val BASE_URL = "https://apis.openapi.sk.com/"

    fun create(): Retrofit {
        // 커스텀 Gson: TmapGeometry에 우리 디시리얼라이저 등록
        val gson = GsonBuilder()
            .registerTypeAdapter(TmapGeometry::class.java, TmapGeometryDeserializer())
            .create()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}