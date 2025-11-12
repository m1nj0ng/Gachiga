package com.miujong.gachiga10

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

// =====================================================================
// [Application 초기 설정]
//  - KakaoMap SDK 초기화
//  - AndroidManifest.xml의 <meta-data>에서 App Key 로드
// =====================================================================
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // -----------------------------------------------------------------
        // KakaoMap 앱 키 로드
        // -----------------------------------------------------------------
        val appKey = try {
            val ai: ApplicationInfo = packageManager.getApplicationInfo(
                packageName, PackageManager.GET_META_DATA
            )
            ai.metaData.getString("com.kakao.vectormap.APP_KEY")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // -----------------------------------------------------------------
        // App Key 누락 시 예외 처리
        // -----------------------------------------------------------------
        if (appKey.isNullOrEmpty()) {
            throw IllegalStateException(
                "KakaoMap SDK AppKey not found. Check AndroidManifest.xml <meta-data>"
            )
        }

        // -----------------------------------------------------------------
        // KakaoMap SDK 초기화
        // -----------------------------------------------------------------
        KakaoMapSdk.init(this, appKey)
    }
}
