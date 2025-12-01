package com.example.gachiga.data

/**
 * 비로그인 버전의 멤버 정보를 담는 데이터 클래스
 */
data class Member(
    val id: Int,                // 고유 ID
    val name: String,
    val startPoint: String = "미설정",
    var x: Double? = null,      // 출발지 경도
    var y: Double? = null,      // 출발지 위도
    var placeName: String = "", // 출발지 명칭
    val color: Int,             // 지도 경로 색상 (ARGB)
    var mode: TravelMode = TravelMode.CAR, // 이동 수단
    var carOption: CarRouteOption = CarRouteOption.RECOMMEND,
    var publicTransitOption: PublicTransitOption = PublicTransitOption.OPTIMAL,

    /**
     * 경로 탐색 옵션
     * - Car: 0(추천), 1(무료), 2(최소시간) ...
     * - Transit: 0(최적), 1(최소환승) ...
     */
    var searchOption: Int = 0
)

/**
 * 비로그인 버전의 약속 정보를 관리하는 데이터 클래스
 */
data class GachigaState(
    val destination: String = "미설정",
    val arrivalTime: String = "14:00",
    val members: List<Member> = listOf(Member(id = 1, name = "멤버 1", color = 0)) // 최소 1명으로 시작
)

data class User(
    val id: String,
    val nickname: String,
    val profileImageUrl: String
)

data class Room(
    val roomId: String,
    val roomName: String,
    val host: User, // 방장 정보
    val members: List<User> // 참여 멤버 목록
)

data class LoggedInState(
    val currentUser: User? = null, // 현재 로그인한 사용자
    val joinedRooms: List<Room> = emptyList() //참여 중인 방 목록
)

data class RoomMember(
    val user: User,
    val startPoint: String = "미설정",
    var x: Double? = null,
    var y: Double? = null,
    val travelMode: TravelMode = TravelMode.CAR,
    val isReady: Boolean = false,
    val voted: Boolean = false,
    val isHost: Boolean = false
)

data class RoomDetail(
    val roomId: String = "TEMP_ID_${System.currentTimeMillis()}", // 임시 고유 ID
    val destination: String = "미설정",
    val arrivalTime: String = "14:00",
    val members: List<RoomMember> = emptyList(),
    val invitationCode: String = (1..6).map { ('A'..'Z') + ('0'..'9') }.map { it.random() }.joinToString(""), // 임시 6자리 코드
    val suggestedRoutes: List<SuggestedRoute> = emptyList(), // 추천 경로 목록 필드 추가
    val finalPlace: String? = null // 최종 결정된 장소 (선택 사항)
)

/**
 * 추천 경로 한 개에 대한 정보 (비로그인/로그인 공용)
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

// 자차 경로 옵션을 정의하는 Enum 클래스 추가
enum class CarRouteOption(val displayName: String) {
    RECOMMEND("추천 경로"),
    SHORTEST("최단 거리"),
    FASTEST("최소 시간"),
    FREE("무료 우선")
}

//대중교통 경로 옵션을 정의하는 Enum 클래스 추가
enum class PublicTransitOption(val displayName: String) {
    OPTIMAL("최적 경로"),
    LEAST_TRANSFER("최소 환승"),
    FASTEST("최소 시간"),
    LEAST_WALKING("최소 도보")
}