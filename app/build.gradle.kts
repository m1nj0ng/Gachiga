import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

// local.properties에서 API Key 로드
val localProps = Properties().apply {
    val f = File(rootProject.projectDir, "local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val kakaoRestKey: String = localProps.getProperty("KAKAO_REST_API_KEY") ?: ""
val tmapAppKey: String = localProps.getProperty("TMAP_APP_KEY") ?: ""

android {
    namespace = "com.miujong.gachiga10"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.miujong.gachiga10"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API Key 주입 (BuildConfig)
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"$kakaoRestKey\"")
        buildConfigField("String", "TMAP_APP_KEY", "\"$tmapAppKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Kakao Maps SDK
    implementation("com.kakao.maps.open:android:2.12.18")

    // Network (Retrofit, OkHttp, Gson, Coroutines)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}