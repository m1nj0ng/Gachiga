package com.example.gachiga

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Kakao SDK 초기화
        KakaoMapSdk.init(this, "dc21e4ca0f1c295746afc0ac8b4789cc")

        // 카카오 로그인 SDK 초기화
        KakaoSdk.init(this, "dc21e4ca0f1c295746afc0ac8b4789cc")
    }
}