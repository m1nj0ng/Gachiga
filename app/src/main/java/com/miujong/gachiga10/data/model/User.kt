package com.miujong.gachiga10.data.model

// 사용자 정보 데이터
data class User(
    val id: Int,
    var name: String,
    var x: Double? = null,
    var y: Double? = null,
    var placeName: String = "",
    val color: Int,
    var mode: TravelMode = TravelMode.CAR,
    var searchOption: Int = 0
)