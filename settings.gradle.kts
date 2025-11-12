// =====================================================================
// [플러그인 관리]
// - Android / Kotlin 플러그인 저장소 정의
// =====================================================================
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// =====================================================================
// [의존성 저장소 관리]
// - 모듈별 build.gradle.kts 에서 개별 선언 방지
// - Kakao Maps / Kakao Mobility 전용 레포지토리 포함
// =====================================================================
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
        maven { url = uri("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/") }
    }
}

// =====================================================================
// [루트 프로젝트 설정]
// =====================================================================
rootProject.name = "Gachiga1.0"
include(":app")
