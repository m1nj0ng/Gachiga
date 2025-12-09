package com.example.gachiga.data

/**
 * 비로그인 버전의 멤버 정보를 담는 데이터 클래스
 */
data class Member(
    val id: Int = 0, // 수정: 기본값 추가
    val name: String = "", // 수정: 기본값 추가
    val startPoint: String = "미설정",
    var x: Double? = null,
    var y: Double? = null,
    var placeName: String = "",
    val color: Int = 0xFF1976D2.toInt(),
    var mode: TravelMode = TravelMode.CAR,
    var carOption: CarRouteOption = CarRouteOption.RECOMMEND,
    var publicTransitOption: PublicTransitOption = PublicTransitOption.OPTIMAL,
    var searchOption: Int = 0
)

/**
 * 비로그인 버전의 약속 정보를 관리하는 데이터 클래스
 * [수정 사항] 목적지 좌표(destX, destY) 필드 추가
 */
data class GachigaState(
    val destination: String = "미설정",
    var destX: Double? = null,
    var destY: Double? = null,
    val arrivalTime: String = "14:00",
    // ★ [수정] 기본 멤버의 색상도 명시적으로 지정하거나 위 기본값 사용
    val members: List<Member> = listOf(Member(id = 1, name = "나", color = 0xFF1976D2.toInt()))
)

data class User( // 수정: 모든 필드에 기본값
    val id: String = "",
    val nickname: String = "",
    val profileImageUrl: String = ""
)

data class Room( // 수정: 모든 필드에 기본값
    val roomId: String = "",
    val roomName: String = "",
    val host: User = User(),
    val members: List<User> = emptyList()
)

data class LoggedInState(
    val currentUser: User? = null,
    val joinedRooms: List<Room> = emptyList()
)

data class RoomMember(
    val user: User = User(), // 수정: 빈 User 객체로 초기화
    val startPoint: String = "미설정",
    var x: Double? = null,      // 출발지 경도 (기존 유지)
    var y: Double? = null,      // 출발지 위도 (기존 유지)
    val travelMode: TravelMode = TravelMode.CAR,

    val carOption: CarRouteOption = CarRouteOption.RECOMMEND,
    val publicTransitOption: PublicTransitOption = PublicTransitOption.OPTIMAL,
    var searchOption: Int = 0,

    // 추가: 상태 메시지와 변경 시간
    val statusMessage: String = "준비 중",
    val statusUpdateTime: Long = 0L,

    // Firebase가 'is'로 시작하는 변수를 잘 읽게 해주기 위한 태그 추가
    @field:JvmField
    val isReady: Boolean = false,
    val voted: Boolean = false,

    @field:JvmField
    val isHost: Boolean = false
)

data class RoomDetail(
    val roomId: String = "", // 수정: 비워둠
    val destination: String = "미설정",

    // ★ [추가됨] 로그인 버전도 목적지 좌표가 필요합니다.
    var destX: Double = 0.0, // 경도
    var destY: Double = 0.0, // 위도

    @field:JvmField
    val isCalculating: Boolean = false, // 리더가 중간 지점 계산을 완료하면 다음 화면으로 넘어가게 하기 위한 변수

    val arrivalTime: String = "14:00",
    val members: List<RoomMember> = emptyList(),
    val invitationCode: String = (1..6).map { ('A'..'Z') + ('0'..'9') }.map { it.random() }.joinToString(""),
    val suggestedRoutes: List<SuggestedRoute> = emptyList(),
    val finalPlace: String? = null,
    val inviteLink: String = "",

    val memberIds: List<String> = emptyList() // 추가: 참여자 ID만 모아놓은 리스트
)

/**
 * 추천 경로 한 개에 대한 정보
 */
data class SuggestedRoute( // 수정: 모든 필드에 기본값 추가
    val id: String= "",
    val placeName: String= "",
    val address: String= "",
    val totalTime: String= "",
    val totalFee: String= "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val voters: List<String> = emptyList(),
    val description: String = ""
)

enum class CarRouteOption(val displayName: String) {
    RECOMMEND("추천 경로"),
    SHORTEST("최단 거리"),
    FASTEST("최소 시간"),
    FREE("무료 우선")
}

enum class PublicTransitOption(val displayName: String) {
    OPTIMAL("최적 경로"),
    LEAST_TRANSFER("최소 환승"),
    FASTEST("최소 시간"),
    LEAST_WALKING("최소 도보")
}

/**
 * [계산 결과 컨테이너]
 * RouteLogicManager가 계산한 데이터를 UI로 운반하는 택배 상자
 */
data class CalculationResult(
    val fullLog: String = "",
    val myLog: String? = null,

    // [내 경로 모드용]
    val myPathPoints: List<com.kakao.vectormap.LatLng>? = null, // 합류 전 (내 색깔)
    val myRedPathPoints: List<com.kakao.vectormap.LatLng>? = null, // ★ [추가] 합류 후 (빨간색)

    // [전체 복구 모드용]
    val allRoutes: Map<Int, TransitPathSegment> = emptyMap(),
    val rawTransitPaths: Map<Int, List<TransitPathSegment>> = emptyMap(),
    val allPointsForCamera: List<com.kakao.vectormap.LatLng> = emptyList(),
    val redLines: List<Pair<List<com.kakao.vectormap.LatLng>, Boolean>> = emptyList(), // 빨간선 복구용

    // ★ [추가] 각 멤버별 경로 자르는 지점 (ID -> Index)
    // 이 정보가 있어야 전체 화면 복구 시 내 경로가 빨간선 위를 덮지 않게(Ghost Path 방지) 자를 수 있습니다.
    val memberCutIndices: Map<Int, Int> = emptyMap()
)