package com.miujong.gachiga10.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// =====================================================================
// [Kakao Local API]
//  - baseUrl: https://dapi.kakao.com/
//  - 키워드로 장소 검색
//  - 응답: documents[] (장소명, 주소, 좌표)
// =====================================================================
interface LocalApiService {
    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Query("query") query: String,     // 검색어
        @Query("size") size: Int = 10      // 결과 개수(기본 10)
    ): KeywordSearchResponse
}

// =====================================================================
// [Retrofit Factory]
// =====================================================================
object LocalRetrofit {
    private const val BASE_URL = "https://dapi.kakao.com/"

    fun create(): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}

// =====================================================================
// [Response Models]
//  - documents[] → Place
//  - x: 경도, y: 위도
// =====================================================================
data class KeywordSearchResponse(
    val documents: List<Place>
)

data class Place(
    @SerializedName("place_name") val placeName: String,
    @SerializedName("address_name") val addressName: String,
    @SerializedName("road_address_name") val roadAddressName: String,
    @SerializedName("x") val x: String,  // 경도
    @SerializedName("y") val y: String   // 위도
)
