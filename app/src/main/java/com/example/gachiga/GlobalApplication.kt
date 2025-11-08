package com.example.gachiga

import android.app.Application
import android.util.Log // 1. Log 임포트
import com.kakao.vectormap.KakaoMapSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Kakao SDK 초기화
        KakaoMapSdk.init(this, "dc21e4ca0f1c295746afc0ac8b4789cc")
    }
}