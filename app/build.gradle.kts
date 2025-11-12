import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

// =====================================================================
// [카카오모빌리티 REST 키 로드]
// - local.properties 파일에서 읽어오며, 하드코딩 금지
// - 예시) 프로젝트 루트/local.properties
//   KAKAO_REST_API_KEY=여기에_키값
// =====================================================================
val localProps = Properties().apply {
    val f = File(rootProject.projectDir, "local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val kakaoRestKey: String = localProps.getProperty("KAKAO_REST_API_KEY") ?: ""

android {
    namespace = "com.miujong.gachiga10"
    compileSdk = 36

    // -----------------------------------------------------------------
    // [빌드 설정]
    // -----------------------------------------------------------------
    buildFeatures {
        buildConfig = true   // BuildConfig 클래스 생성 활성화
    }

    defaultConfig {
        applicationId = "com.miujong.gachiga10"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // -------------------------------------------------------------
        // REST 키를 BuildConfig 에 주입
        // 코드에서 BuildConfig.KAKAO_REST_API_KEY 로 접근 가능
        // -------------------------------------------------------------
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestKey\"")
    }

    // -----------------------------------------------------------------
    // [빌드 타입]
    // -----------------------------------------------------------------
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 필요 시 네트워크 로그 출력 등 디버그용 설정 추가 가능
        }
    }

    // -----------------------------------------------------------------
    // [컴파일 옵션]
    // -----------------------------------------------------------------
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // =================================================================
    // [AndroidX 기본 라이브러리]
    // =================================================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // =================================================================
    // [Kakao Maps SDK v2]
    // - 지도 렌더링 및 마커 표시
    // =================================================================
    implementation("com.kakao.maps.open:android:2.12.18")

    // =================================================================
    // [Kakao Mobility REST 호출 구성]
    // - OkHttp / Retrofit / Gson / Coroutines
    // =================================================================
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // =================================================================
    // [Kakao SDK (선택)]
    // - 현재 로그인/공유 기능 사용 안 함
    // - 필요 시 주석 해제 가능
    // =================================================================
    // implementation("com.kakao.sdk:v2-all:2.20.1")
    // implementation("com.kakao.sdk:v2-user:2.20.1")
    // implementation("com.kakao.sdk:v2-share:2.20.1")
    // implementation("com.kakao.sdk:v2-talk:2.20.1")
    // implementation("com.kakao.sdk:v2-friend:2.20.1")
    // implementation("com.kakao.sdk:v2-navi:2.20.1")
    // implementation("com.kakao.sdk:v2-cert:2.20.1")

    // =================================================================
    // [테스트 라이브러리]
    // =================================================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
