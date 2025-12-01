package com.example.gachiga.data

/**
 * 비로그인 버전의 멤버 정보를 담는 데이터 클래스
 */
data class Member(
    val id: Int,
    val name: String,
    val startPoint: String = "미설정",
    var x: Double? = null,      // 출발지 경도 (기존 유지)
    var y: Double? = null,      // 출발지 위도 (기존 유지)
    var placeName: String = "", // 출발지 명칭 (기존 유지 - 로직에서 사용)
    val color: Int,
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

    // ★ [추가됨] 로직 계산을 위한 목적지 좌표
    var destX: Double? = null, // 경도 (Longitude)
    var destY: Double? = null, // 위도 (Latitude)

    val arrivalTime: String = "14:00",
    val members: List<Member> = listOf(Member(id = 1, name = "멤버 1", color = 0))
)

data class User(
    val id: String,
    val nickname: String,
    val profileImageUrl: String
)

data class Room(
    val roomId: String,
    val roomName: String,
    val host: User,
    val members: List<User>
)

data class LoggedInState(
    val currentUser: User? = null,
    val joinedRooms: List<Room> = emptyList()
)

data class RoomMember(
    val user: User,
    val startPoint: String = "미설정",
    var x: Double? = null,      // 출발지 경도 (기존 유지)
    var y: Double? = null,      // 출발지 위도 (기존 유지)
    val travelMode: TravelMode = TravelMode.CAR,
    val isReady: Boolean = false,
    val voted: Boolean = false,
    val isHost: Boolean = false
)

data class RoomDetail(
    val roomId: String = "TEMP_ID_${System.currentTimeMillis()}",
    val destination: String = "미설정",

    // ★ [추가됨] 로그인 버전도 목적지 좌표가 필요합니다.
    var destX: Double? = null, // 경도
    var destY: Double? = null, // 위도

    val arrivalTime: String = "14:00",
    val members: List<RoomMember> = emptyList(),
    val invitationCode: String = (1..6).map { ('A'..'Z') + ('0'..'9') }.map { it.random() }.joinToString(""),
    val suggestedRoutes: List<SuggestedRoute> = emptyList(),
    val finalPlace: String? = null
)

/**
 * 추천 경로 한 개에 대한 정보
 */
data class SuggestedRoute(
    val id: String,
    val placeName: String,
    val address: String,
    val totalTime: String,
    val totalFee: String,
    val latitude: Double,
    val longitude: Double,
    val voters: List<String> = emptyList()
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