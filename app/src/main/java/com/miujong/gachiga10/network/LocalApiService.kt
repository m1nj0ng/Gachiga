package com.miujong.gachiga10.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * [카카오 로컬 API 인터페이스]
 * - 역할: 좌표 <-> 주소 변환, 키워드 장소 검색
 * - 사용처: 합류 지점(좌표)이 정해졌을 때, 그곳이 "어디인지(명칭)"를 알아내기 위해 사용합니다.
 */
interface LocalApiService {

    /**
     * 1. 키워드 검색
     * 사용자가 입력한 검색어(예: "중앙대")에 대한 장소 리스트를 반환합니다.
     */
    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Query("query") query: String,
        @Query("size") size: Int = 10
    ): KeywordSearchResponse

    /**
     * 2. 카테고리 검색
     * 특정 좌표 주변의 지하철역(SW8), 카페(CE7) 등을 찾아 합류 지점 명칭으로 사용합니다.
     */
    @GET("v2/local/search/category.json")
    suspend fun searchByCategory(
        @Query("category_group_code") categoryCode: String,
        @Query("x") x: String,
        @Query("y") y: String,
        @Query("radius") radius: Int,
        @Query("sort") sort: String = "distance"
    ): KeywordSearchResponse

    /**
     * 3. 좌표 -> 주소 변환
     * 주변에 랜드마크가 없을 때, '도로명 주소'나 '지번 주소'를 얻기 위해 사용합니다.
     */
    @GET("v2/local/geo/coord2address.json")
    suspend fun coord2address(
        @Query("x") x: String,
        @Query("y") y: String
    ): AddressResponse
}

// ------------------------------------------------------------
// [응답 데이터 모델]
// ------------------------------------------------------------

/**
 * [키워드/카테고리 검색 응답 래퍼]
 * 카카오 API는 결과 리스트를 'documents'라는 키로 감싸서 줍니다.
 */
data class KeywordSearchResponse(
    val documents: List<Place>
)

/**
 * [장소 정보 객체]
 * 검색된 개별 장소의 이름, 주소, 좌표 정보를 담고 있습니다.
 */
data class Place(
    @SerializedName("place_name") val placeName: String, // 장소명 (예: 중앙대학교)
    @SerializedName("address_name") val addressName: String, // 지번 주소
    @SerializedName("road_address_name") val roadAddressName: String, // 도로명 주소
    @SerializedName("x") val x: String, // 경도 (Longitude)
    @SerializedName("y") val y: String  // 위도 (Latitude)
)

/**
 * [주소 변환 응답 래퍼]
 * 좌표로 주소를 검색했을 때의 결과입니다.
 */
data class AddressResponse(
    val documents: List<AddressDocument>
)

/**
 * [주소 문서 객체]
 * 구주소(address)와 신주소(roadAddress) 정보를 모두 포함합니다.
 */
data class AddressDocument(
    @SerializedName("address") val address: AddressInfo?,
    @SerializedName("road_address") val roadAddress: RoadAddressInfo?
)

data class AddressInfo(@SerializedName("address_name") val addressName: String)
data class RoadAddressInfo(@SerializedName("address_name") val addressName: String)