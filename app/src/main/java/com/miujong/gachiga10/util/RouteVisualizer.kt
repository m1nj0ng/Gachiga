package com.miujong.gachiga10.util

import android.graphics.Color
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.miujong.gachiga10.data.model.TransitPathSegment

/**
 * [지도 시각화 담당 (Painter)]
 * - 역할: KakaoMap 객체를 직접 제어하여 선(Polyline)을 그리고, 지우고, 카메라를 이동합니다.
 * - 특징: 비즈니스 로직(계산)은 전혀 모르며, LogicManager가 "이 좌표를 빨간색으로 그려"라고 시키면 수행만 합니다.
 */
class RouteVisualizer(private val kakaoMap: KakaoMap) {

    // 기본 경로를 그리는 레이어 (파란색, 초록색 등)
    private val routeLayer = kakaoMap.shapeManager?.addLayer(ShapeLayerOptions.from("tmap_routes"))

    // 그려진 선 객체들을 관리하는 리스트 (나중에 clear() 할 때 사용)
    private val currentPolylines = mutableListOf<Polyline>()

    /**
     * [초기화] 지도 위에 그려진 모든 선을 지웁니다.
     * 새로운 경로 계산 시 화면을 깨끗하게 비우기 위해 호출됩니다.
     */
    fun clear() {
        currentPolylines.forEach { it.remove() }
        currentPolylines.clear()
        // 'overlaps' 레이어(빨간 합류선)도 함께 제거
        kakaoMap.shapeManager?.getLayer("overlaps")?.removeAll()
    }

    /**
     * [기본 선 그리기]
     * 자동차, 도보, 혹은 합류 전의 일반 경로를 단색으로 그립니다.
     */
    fun drawPolyline(points: List<LatLng>, color: Int) {
        if (points.isEmpty()) return
        val opts = PolylineOptions.from(MapPoints.fromLatLng(points), 12f, color)
        routeLayer?.addPolyline(opts)?.let { currentPolylines.add(it) }
    }

    /**
     * [합류 구간 강조]
     * 합류 지점부터 목적지까지의 경로를 '빨간색 굵은 선'으로 덧칠(Overlay)합니다.
     * 별도의 'overlaps' 레이어를 사용하여 기존 선 위에 덮어씌웁니다.
     */
    fun drawRedLine(points: List<LatLng>) {
        if (points.isEmpty()) return
        val sm = kakaoMap.shapeManager ?: return
        val layer = sm.getLayer("overlaps") ?: sm.addLayer(ShapeLayerOptions.from("overlaps"))

        // 두께 12f, 빨간색 (ARGB)
        val opts = PolylineOptions.from(MapPoints.fromLatLng(points), 12f, 0xFFFF0000.toInt())
        layer.addPolyline(opts)
    }

    /**
     * [대중교통 경로 자르기 & 색상 복원]
     * 대중교통 경로는 구간(Segment)마다 색상(2호선, 9호선 등)이 다릅니다.
     * 이를 살리면서 합류 지점까지만 잘라서 그리는 복합적인 로직입니다.
     *
     * @param segments 대중교통 상세 구간 리스트
     * @param cutLimitTotalIndex 합류 지점까지의 누적 인덱스 (여기까지만 그림)
     * @param userColor 사용자 고유 색상 (심지 색상)
     */
    fun drawTransitRouteCut(segments: List<TransitPathSegment>, cutLimitTotalIndex: Int, userColor: Int) {
        var currentIndex = 0

        for (seg in segments) {
            val segSize = seg.points.size
            if (segSize == 0) continue

            if (currentIndex + segSize <= cutLimitTotalIndex) {
                // [Case 1] 이 구간은 온전히 그려야 함
                drawDualColorPolyline(seg.points, seg.color, userColor)
                currentIndex += segSize
            } else if (currentIndex < cutLimitTotalIndex) {
                // [Case 2] 이 구간 중간에서 잘라야 함 (합류 지점 포함 구간)
                val takeCount = cutLimitTotalIndex - currentIndex
                val partialPoints = seg.points.take(takeCount + 1)
                drawDualColorPolyline(partialPoints, seg.color, userColor)
                break // 이후 구간은 그리지 않음
            } else {
                // [Case 3] 이미 합류 지점을 지난 구간 (Skip)
                break
            }
        }
    }

    /**
     * [카메라 자동 이동]
     * 모든 경로 포인트가 화면에 들어오도록 줌 레벨과 위치를 자동 조정합니다.
     * padding(80)을 주어 경로가 화면 가장자리에 잘리지 않게 합니다.
     */
    fun moveCameraToFit(points: List<LatLng>) {
        if (points.isEmpty()) return
        val cameraUpdate = com.kakao.vectormap.camera.CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 80)
        kakaoMap.moveCamera(cameraUpdate)
    }

    /**
     * [Helper] 이중 색상 선 그리기 (테두리 + 심지)
     * 대중교통 노선색(테두리) 위에 사용자색(심지)을 얹어서,
     * "2호선을 타고 가는 사용자 A"임을 시각적으로 표현합니다.
     */
    private fun drawDualColorPolyline(points: List<LatLng>, transitColorStr: String?, userColor: Int) {
        if (points.isEmpty()) return
        val transitColor = parseColorSafe(transitColorStr)

        // 1. 테두리 (노선색, 20f)
        val optsBorder = PolylineOptions.from(MapPoints.fromLatLng(points), 20f, transitColor)
        routeLayer?.addPolyline(optsBorder)?.let { currentPolylines.add(it) }

        // 2. 심지 (유저색, 12f)
        val optsCore = PolylineOptions.from(MapPoints.fromLatLng(points), 12f, userColor)
        routeLayer?.addPolyline(optsCore)?.let { currentPolylines.add(it) }
    }

    // 색상 문자열 파싱 (실패 시 기본 회색)
    private fun parseColorSafe(colorStr: String?): Int {
        return try {
            val c = colorStr ?: "#888888"
            Color.parseColor(if (c.startsWith("#")) c else "#$c")
        } catch (e: Exception) { 0xFF888888.toInt() }
    }
}