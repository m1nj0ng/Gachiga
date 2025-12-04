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

data class AddressResponse(
    val documents: List<AddressDocument>
)

data class AddressDocument(
    @SerializedName("address") val address: AddressInfo?,
    @SerializedName("road_address") val roadAddress: RoadAddressInfo?
)

data class AddressInfo(@SerializedName("address_name") val addressName: String)
data class RoadAddressInfo(@SerializedName("address_name") val addressName: String)

// --- Retrofit 인터페이스 정의 ---

interface KakaoApiService {
    // 키워드로 장소 검색
    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Header("Authorization") apiKey: String,
        @Query("query") keyword: String,
        @Query("size") size: Int = 10
    ): KeywordSearchResponse

    @GET("v2/local/search/category.json")
    suspend fun searchByCategory(
        @Query("category_group_code") categoryCode: String,
        @Query("x") x: String,
        @Query("y") y: String,
        @Query("radius") radius: Int,
        @Query("sort") sort: String = "distance"
    ): KeywordSearchResponse

    @GET("v2/local/geo/coord2address.json")
    suspend fun coord2address(
        @Header("Authorization") apiKey: String,
        @Query("x") x: String,
        @Query("y") y: String
    ): AddressResponse
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