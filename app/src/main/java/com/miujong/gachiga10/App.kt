package com.miujong.gachiga10

import android.app.Application
import android.content.pm.PackageManager
import com.kakao.vectormap.KakaoMapSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // AndroidManifest.xml에서 APP_KEY 가져오기
        val appKey = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.kakao.vectormap.APP_KEY")
        } catch (e: Exception) {
            null
        }

        if (appKey.isNullOrBlank()) {
            throw IllegalStateException("KakaoMap SDK AppKey not found in AndroidManifest.xml")
        }

        // SDK 초기화
        KakaoMapSdk.init(this, appKey)
    }
}