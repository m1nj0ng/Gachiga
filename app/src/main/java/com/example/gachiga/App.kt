package com.example.gachiga

import android.app.Application
import android.content.pm.PackageManager
import com.kakao.vectormap.KakaoMapSdk

/**
 * [전역 애플리케이션 클래스]
 * - 앱 프로세스가 시작될 때 가장 먼저 실행되는 진입점(Entry Point)입니다.
 * - 역할: KakaoMap SDK 초기화 등 앱 실행 내내 유지되어야 할 전역 설정을 담당합니다.
 * - 주의: AndroidManifest.xml의 <application android:name=".App"> 에 반드시 등록되어 있어야 작동합니다.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. [API Key 추출] AndroidManifest.xml의 <meta-data> 태그에서 값 읽어오기
        // 소스 코드에 키를 직접 적지 않고(Hard-coding 지양), Manifest의 메타데이터로 분리하여 관리합니다.
        val appKey = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.kakao.vectormap.APP_KEY")
        } catch (e: Exception) {
            null
        }

        // 2. [유효성 검사] 키가 없으면 명시적으로 에러 발생 (Fail Fast)
        // 지도가 핵심 기능인 앱이므로, 키 없이 앱이 켜지는 것을 방지합니다.
        if (appKey.isNullOrBlank()) {
            throw IllegalStateException("KakaoMap SDK AppKey not found in AndroidManifest.xml")
        }

        // 3. [SDK 초기화]
        // 이 코드가 실행된 이후부터 MapView를 사용할 수 있습니다.
        KakaoMapSdk.init(this, appKey)
    }
}