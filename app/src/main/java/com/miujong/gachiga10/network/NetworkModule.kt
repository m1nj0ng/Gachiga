package com.miujong.gachiga10.network

import com.google.gson.GsonBuilder
import com.miujong.gachiga10.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private const val TMAP_URL = "https://apis.openapi.sk.com/"
    private const val KAKAO_URL = "https://dapi.kakao.com/"

    private fun createClient(headers: Map<String, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                headers.forEach { (k, v) -> req.addHeader(k, v) }
                chain.proceed(req.build())
            }.build()
    }

    // [공통] TmapGeometry 해석기 (자동차/보행자 공통 사용)
    private val tmapGson = GsonBuilder()
        .registerTypeAdapter(TmapGeometry::class.java, TmapGeometryDeserializer())
        .create()

    // 1. 자동차 API
    fun getTmapRouteApi(): TmapRouteApiService {
        return Retrofit.Builder()
            .baseUrl(TMAP_URL)
            .addConverterFactory(GsonConverterFactory.create(tmapGson)) // ★ 커스텀 Gson
            .client(createClient(mapOf("appKey" to BuildConfig.TMAP_APP_KEY, "Accept" to "application/json")))
            .build()
            .create(TmapRouteApiService::class.java)
    }

    // 2. 대중교통 API
    fun getTransitApi(): TransitApiService {
        return Retrofit.Builder()
            .baseUrl(TMAP_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(createClient(mapOf("appKey" to BuildConfig.TMAP_APP_KEY, "Content-Type" to "application/json")))
            .build()
            .create(TransitApiService::class.java)
    }

    // 3. 보행자 API
    fun getTmapWalkApi(): TmapWalkApiService {
        return Retrofit.Builder()
            .baseUrl(TMAP_URL)
            .addConverterFactory(GsonConverterFactory.create(tmapGson)) // ★ [수정] 기본 Gson -> tmapGson으로 변경!
            .client(createClient(mapOf("appKey" to BuildConfig.TMAP_APP_KEY)))
            .build()
            .create(TmapWalkApiService::class.java)
    }

    // 4. 로컬 API
    fun getLocalApi(): LocalApiService {
        return Retrofit.Builder()
            .baseUrl(KAKAO_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(createClient(mapOf("Authorization" to "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")))
            .build()
            .create(LocalApiService::class.java)
    }
}