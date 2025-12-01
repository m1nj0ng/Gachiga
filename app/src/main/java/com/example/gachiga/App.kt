package com.example.gachiga // 패키지명 확인!

import android.app.Application
import android.content.pm.PackageManager
import com.kakao.sdk.common.KakaoSdk // ★ 이 import 필수
import com.kakao.vectormap.KakaoMapSdk

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Manifest에서 키 추출
        val appKey = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.kakao.vectormap.APP_KEY")
        } catch (e: Exception) {
            null
        }

        if (appKey.isNullOrBlank()) {
            throw IllegalStateException("Kakao AppKey not found in AndroidManifest.xml")
        }

        // 2. SDK 초기화 (둘 다 해야 함!)

        // 지도 SDK
        KakaoMapSdk.init(this, appKey)

        // ★ [추가됨] 로그인 SDK (이게 있어야 로그인이 됩니다)
        KakaoSdk.init(this, appKey)
    }
}