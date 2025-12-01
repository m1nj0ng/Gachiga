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
        // [new-ui] 카카오 플러그인용 (팀원 설정 유지)
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // 1. [공통] 카카오 통합 저장소 (로그인 등)
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }

        // 2. ★ [map-api-test] 카카오 맵 전용 저장소 (이거 없으면 지도 SDK 다운로드 실패함!)
        maven { url = uri("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/") }
    }
}

// 프로젝트 이름은 팀원 버전인 "Gachiga"로 통일 (충돌 방지)
rootProject.name = "Gachiga"
include(":app")
