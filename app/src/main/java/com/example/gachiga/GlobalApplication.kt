package com.example.gachiga

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Kakao SDK 초기화
        KakaoMapSdk.init(this, "cd471e27ab571998414af9f581d42cd5")

        // 카카오 로그인 SDK 초기화
        KakaoSdk.init(this, "cd471e27ab571998414af9f581d42cd5")
    }
}