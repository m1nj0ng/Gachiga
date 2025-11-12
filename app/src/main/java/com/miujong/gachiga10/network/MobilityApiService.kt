package com.miujong.gachiga10.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// =====================================================================
// [Kakao Mobility Directions API]
//  - baseUrl: https://apis-navi.kakaomobility.com/
//  - origin/destination: "x,y" 형식 (경도,위도)
//  - priority: RECOMMEND | TIME | DISTANCE
//  - summary: true → 요약, false → 전체 좌표 포함
//  - alternatives: true → 대안 경로 포함
// =====================================================================
interface MobilityApiService {
    @GET("v1/directions")
    suspend fun getDirections(
        @Query("origin") origin: String,              // "x,y"
        @Query("destination") destination: String,    // "x,y"
        @Query("priority") priority: String = "RECOMMEND",
        @Query("summary") summary: Boolean = false,
        @Query("alternatives") alternatives: Boolean = true
    ): DirectionsResponse
}

// =====================================================================
// [Retrofit Factory]
// =====================================================================
object MobilityRetrofit {
    private const val BASE_URL = "https://apis-navi.kakaomobility.com/"

    fun create(): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}

// =====================================================================
// [Response Models]
//  - Section 내부에 Road[] 포함
//  - Road.vertexes: [x,y,x,y,...] 형태의 배열
//  - Summary: 거리/시간/요금 등 주요 정보
// =====================================================================
data class DirectionsResponse(
    val routes: List<Route>?
)

data class Route(
    val summary: Summary?,
    val sections: List<Section>?
)

data class Section(
    val roads: List<Road>?
)

data class Road(
    val vertexes: List<Double>?
)

data class Summary(
    val distance: Int?,        // 거리(m)
    val duration: Int?,        // 시간(s)
    val fare: Fare?
)

data class Fare(
    val toll: Int?,            // 톨게이트 요금(원)
    val taxi: Int?,            // 택시 요금 추정(원)
    @SerializedName("fuel") val fuel: Int? = null,
    @SerializedName("congestion") val congestion: Int? = null
)
