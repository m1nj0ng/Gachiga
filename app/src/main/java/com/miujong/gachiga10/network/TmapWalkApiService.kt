package com.miujong.gachiga10.network

import retrofit2.http.GET
import retrofit2.http.Query

interface TmapWalkApiService {
    @GET("tmap/routes/pedestrian")
    suspend fun getWalkRoute(
        @Query("startX") startX: Double,
        @Query("startY") startY: Double,
        @Query("endX") endX: Double,
        @Query("endY") endY: Double,
        @Query("startName") startName: String,
        @Query("endName") endName: String,
        @Query("version") version: Int = 1,
        @Query("format") format: String = "json"
    ): TmapRouteResponse
}
