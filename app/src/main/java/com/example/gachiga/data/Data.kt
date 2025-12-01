package com.example.gachiga.data

/**
 * 비로그인 버전의 멤버 정보를 담는 데이터 클래스
 */
data class Member(
    val id: Int,
    val name: String,
    val startPoint: String = "미설정",
    var x: Double? = null,
    var y: Double? = null,
    var placeName: String = "",
    // ★ [수정] 0 대신 확실한 색상값(파란색)을 기본값으로 지정
    val color: Int = -16776961, // Color.BLUE (0xFF0000FF)
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
    val members: List<Member> = listOf(Member(id = 1, name = "나", color = -16776961))
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