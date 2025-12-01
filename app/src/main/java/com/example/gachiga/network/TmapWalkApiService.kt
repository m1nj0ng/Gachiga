package com.example.gachiga.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * [TMAP 보행자 경로 API]
 * - 역할: 도보 이동 경로 요청
 * - 특징: 자동차 API와 응답 구조(TmapRouteResponse)를 공유하지만, 요청 파라미터(startName, endName)가 다릅니다.
 */
interface TmapWalkApiService {
    @GET("tmap/routes/pedestrian")
    suspend fun getWalkRoute(
        @Query("startX") startX: Double,
        @Query("startY") startY: Double,
        @Query("endX") endX: Double,
        @Query("endY") endY: Double,
        @Query("startName") startName: String, // (필수) URL 인코딩된 출발지 명칭
        @Query("endName") endName: String,     // (필수) URL 인코딩된 도착지 명칭
        @Query("version") version: Int = 1,
        @Query("format") format: String = "json"
    ): TmapRouteResponse
}