package com.miujong.gachiga10.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// 카카오 로컬 API 인터페이스
interface LocalApiService {

    // 1. 키워드 검색 (목적지 검색용)
    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Query("query") query: String,
        @Query("size") size: Int = 10
    ): KeywordSearchResponse

    // 2. 카테고리 검색 (합류 지점 주변 랜드마크 탐색용)
    // category_group_code: SW8(지하철), CE7(카페), CS2(편의점) 등
    @GET("v2/local/search/category.json")
    suspend fun searchByCategory(
        @Query("category_group_code") categoryCode: String,
        @Query("x") x: String,       // 경도 (Longitude)
        @Query("y") y: String,       // 위도 (Latitude)
        @Query("radius") radius: Int, // 검색 반경 (미터)
        @Query("sort") sort: String = "distance" // 거리순 정렬
    ): KeywordSearchResponse

    // 3. 주소 변환 (좌표 -> 주소)
    // 주변에 아무런 건물이 없을 때 주소라도 보여주기 위함
    @GET("v2/local/geo/coord2address.json")
    suspend fun coord2address(
        @Query("x") x: String,
        @Query("y") y: String
    ): AddressResponse
}

// ------------------------------------------------------------
// 데이터 모델 (응답 구조)
// ------------------------------------------------------------

// 장소 검색 결과 (키워드/카테고리 공용)
data class KeywordSearchResponse(
    val documents: List<Place>
)

data class Place(
    @SerializedName("place_name") val placeName: String,
    @SerializedName("address_name") val addressName: String,
    @SerializedName("road_address_name") val roadAddressName: String,
    @SerializedName("x") val x: String,
    @SerializedName("y") val y: String
)

// 주소 변환 결과
data class AddressResponse(
    val documents: List<AddressDocument>
)

data class AddressDocument(
    @SerializedName("address") val address: AddressInfo?,
    @SerializedName("road_address") val roadAddress: RoadAddressInfo?
)

data class AddressInfo(@SerializedName("address_name") val addressName: String)
data class RoadAddressInfo(@SerializedName("address_name") val addressName: String)