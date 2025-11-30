package com.example.gachiga.data

import com.kakao.vectormap.LatLng

data class GachigaState(
    val members: List<Member> = listOf(Member(name = "멤버 1")),
    val destination: String = "미설정",
    val arrivalTime: String = "14:00"
)

data class Member(
    val name: String,
    val startPoint: String = "미설정",
    val usePublicTransit: Boolean = true, // 대중교통 선택 여부 (기본값 true)
    val useCar: Boolean = false             // 자차 선택 여부
)

data class User(
    val id: String = "",
    val nickname: String = "",
    val profileImageUrl: String = ""
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
    val user: User = User(),
    val startPoint: String = "미설정",
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val usePublicTransit: Boolean = true,
    val useCar: Boolean = false,

    @field:JvmField
    val isReady: Boolean = false,

    @field:JvmField
    val isHost: Boolean = false
)

data class RoomDetail(
    val roomId: String = "TEMP_ID_${System.currentTimeMillis()}", // 임시 고유 ID
    val roomName: String = "새로운 약속",
    val destination: String = "미설정",
    val destLat: Double = 0.0, // Latitude (위도, y)
    val destLng: Double = 0.0, // Longitude (경도, x)
    val arrivalTime: String = "14:00",
    val members: List<RoomMember> = emptyList(),
    val invitationCode: String = ""
)