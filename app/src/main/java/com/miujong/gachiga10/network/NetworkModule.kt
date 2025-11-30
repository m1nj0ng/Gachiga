package com.miujong.gachiga10.network

import com.google.gson.GsonBuilder
import com.miujong.gachiga10.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * [네트워크 모듈]
 * TMAP 및 Kakao API 통신을 위한 Retrofit 인스턴스를 생성하고 관리하는 싱글톤 객체입니다.
 * 각 API 특성에 맞는 Converter(Gson)와 Header 설정을 담당합니다.
 */
object NetworkModule {
    private const val TMAP_URL = "https://apis.openapi.sk.com/"
    private const val KAKAO_URL = "https://dapi.kakao.com/"

    /**
     * 공통 OkHttpClient 생성
     * - API 통신 로그(HttpLoggingInterceptor) 설정
     * - 공통 헤더(AppKey, Authorization) 주입
     */
    private fun createClient(headers: Map<String, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                headers.forEach { (k, v) -> req.addHeader(k, v) }
                chain.proceed(req.build())
            }.build()
    }

    /**
     * [TMAP 전용 Geometry 파서]
     * 자동차/보행자 API의 Geometry 데이터(LineString, MultiLineString 등)가
     * 다형성(Polymorphism)을 띠고 있어, 이를 처리하기 위한 커스텀 Deserializer를 등록합니다.
     */
    private val tmapGson = GsonBuilder()
        .registerTypeAdapter(TmapGeometry::class.java, TmapGeometryDeserializer())
        .create()

    // 1. 자동차 경로 API (커스텀 Gson 사용 - Geometry 파싱 필요)
    fun getTmapRouteApi(): TmapRouteApiService {
        return Retrofit.Builder()
            .baseUrl(TMAP_URL)
            .addConverterFactory(GsonConverterFactory.create(tmapGson)) // ★ Custom Gson
            .client(createClient(mapOf("appKey" to BuildConfig.TMAP_APP_KEY, "Accept" to "application/json")))
            .build()
            .create(TmapRouteApiService::class.java)
    }

    // 2. 대중교통 경로 API (기본 Gson 사용 - 표준 JSON 구조)
    fun getTransitApi(): TransitApiService {
        return Retrofit.Builder()
            .baseUrl(TMAP_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Standard Gson
            .client(createClient(mapOf("appKey" to BuildConfig.TMAP_APP_KEY, "Content-Type" to "application/json")))
            .build()
            .create(TransitApiService::class.java)
    }

    // 3. 보행자 경로 API (커스텀 Gson 사용 - Geometry 파싱 필요)
    fun getTmapWalkApi(): TmapWalkApiService {
        return Retrofit.Builder()
            .baseUrl(TMAP_URL)
            .addConverterFactory(GsonConverterFactory.create(tmapGson)) // ★ Custom Gson
            .client(createClient(mapOf("appKey" to BuildConfig.TMAP_APP_KEY)))
            .build()
            .create(TmapWalkApiService::class.java)
    }

    // 4. 카카오 로컬 API (장소/주소 검색용)
    fun getLocalApi(): LocalApiService {
        return Retrofit.Builder()
            .baseUrl(KAKAO_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(createClient(mapOf("Authorization" to "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")))
            .build()
            .create(LocalApiService::class.java)
    }
}