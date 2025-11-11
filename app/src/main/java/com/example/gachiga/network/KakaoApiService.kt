package com.example.gachiga.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// --- API 응답 데이터 구조에 맞는 데이터 클래스 정의 ---

// 키워드 검색 응답 전체 구조
data class KeywordSearchResponse(
    val documents: List<Place>
)

// 장소(Place) 정보 구조
data class Place(
    @SerializedName("place_name") val placeName: String, // 장소명
    @SerializedName("address_name") val addressName: String, // 지번 주소
    @SerializedName("road_address_name") val roadAddressName: String, // 도로명 주소
    @SerializedName("x") val longitude: String, // 경도
    @SerializedName("y") val latitude: String  // 위도
)

// --- Retrofit 인터페이스 정의 ---

interface KakaoApiService {
    // 키워드로 장소 검색
    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Header("Authorization") apiKey: String,
        @Query("query") keyword: String
    ): KeywordSearchResponse
}

// --- Retrofit 객체를 생성하는 싱글턴 객체 ---

object RetrofitInstance {
    private const val BASE_URL = "https://dapi.kakao.com/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: KakaoApiService by lazy {
        retrofit.create(KakaoApiService::class.java)
    }
}