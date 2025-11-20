package com.miujong.gachiga10.util

import com.kakao.vectormap.LatLng
import java.lang.Math.toRadians
import kotlin.math.*
import com.google.gson.internal.LinkedTreeMap // JSON 파싱용

object RouteMath {

    // 공유 구간 찾기 (전체)
    fun findAllSharedSegments(routes: List<List<LatLng>>): List<List<LatLng>> {
        val results = mutableListOf<List<LatLng>>()
        for (i in 0 until routes.size - 1) {
            for (j in i + 1 until routes.size) {
                results.addAll(findSharedSegments(routes[i], routes[j]))
            }
        }
        return results
    }

    // 공유 구간 찾기 (두 경로 간)
    private fun findSharedSegments(
        a: List<LatLng>, b: List<LatLng>,
        tolMeters: Double = 30.0, angleTol: Double = 45.0
    ): List<List<LatLng>> {
        val out = mutableListOf<List<LatLng>>()
        var i = 0

        fun dist(p1: LatLng, p2: LatLng) = haversineMeters(p1, p2)

        while (i < a.size) {
            val nearIdx = b.indexOfFirst { dist(a[i], it) <= tolMeters }
            if (nearIdx == -1) { i++; continue }

            val segment = mutableListOf(a[i])
            var ia = i
            var jb = nearIdx

            while (ia + 1 < a.size) {
                val nextA = a[ia + 1]
                val searchRange = (jb + 1)..min(b.lastIndex, jb + 5)
                val bestJ = searchRange.minByOrNull { dist(nextA, b[it]) } ?: break

                if (dist(nextA, b[bestJ]) > tolMeters) break

                val angA = bearingDeg(segment.last(), nextA)
                val angB = bearingDeg(b[jb], b[bestJ])
                if (angleDiffDeg(angA, angB) > angleTol) break

                segment.add(nextA)
                ia++
                jb = bestJ
            }

            if (segment.size >= 6) out.add(segment)
            i = ia + 1
        }
        return out
    }

    // 대중교통 좌표 파싱
    fun parsePassShape(passShape: Any?): List<LatLng> {
        val shapeString = when (passShape) {
            is String -> passShape
            is LinkedTreeMap<*, *> -> passShape["linestring"] as? String
            else -> null
        }

        if (shapeString.isNullOrBlank()) return emptyList()

        return try {
            shapeString.trim().split(Regex("\\s+")).mapNotNull { coords ->
                val split = coords.split(",")
                if (split.size == 2) {
                    val lon = split[0].trim().toDoubleOrNull() ?: 0.0
                    val lat = split[1].trim().toDoubleOrNull() ?: 0.0
                    if (lon != 0.0 && lat != 0.0) LatLng.from(lat, lon) else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 거리 계산
    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val lat1 = toRadians(a.latitude); val lat2 = toRadians(b.latitude)
        val dLat = toRadians(b.latitude - a.latitude)
        val dLon = toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    // 각도 계산
    private fun bearingDeg(p: LatLng, q: LatLng): Double {
        val lat1 = toRadians(p.latitude); val lat2 = toRadians(q.latitude)
        val dLon = toRadians(q.longitude - p.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // 각도 차이
    private fun angleDiffDeg(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180) 360 - diff else diff
    }
}