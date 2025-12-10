package com.example.gachiga.util

import android.graphics.Color
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeManager
import com.example.gachiga.data.TransitPathSegment

/**
 * [지도 시각화 담당 (Painter)]
 * - 역할: 모든 경로를 '테두리(Border) + 심지(Core)' 형태의 이중선으로 통일감 있게 그립니다.
 */
class RouteVisualizer(private val kakaoMap: KakaoMap) {

    // ========================================================================
    // [상수 정의] 두께 및 색상 (여기를 조정하면 전체 경로 스타일이 바뀝니다)
    // ========================================================================
    companion object {
        // 일반 경로 두께
        private const val STD_BORDER_WIDTH = 15f
        private const val STD_CORE_WIDTH = 9f

        // 자동차/도보용 강조 두께 (합류 시)
        private const val EMPHASIS_BORDER_WIDTH = 15f
        private const val EMPHASIS_CORE_WIDTH = 9f

        private val COLOR_GRAY_BORDER = Color.parseColor("#AAAAAA")
        private val COLOR_RED_BORDER = Color.parseColor("#CC0000") // 진한 빨강
        private val COLOR_RED_CORE = Color.RED                     // 밝은 빨강
        private val COLOR_DEFAULT_TRANSIT = Color.parseColor("#888888")
    }

    private val shapeManager: ShapeManager? = kakaoMap.shapeManager
    // 기본 경로 레이어 (파랑, 초록 등)
    private val routeLayer = shapeManager?.addLayer(ShapeLayerOptions.from("tmap_routes"))
    // 합류 강조 레이어 (빨간선 - 항상 가장 위에 그려짐)
    private val overlapLayer = shapeManager?.getLayer("overlaps") ?: shapeManager?.addLayer(ShapeLayerOptions.from("overlaps"))

    // 그려진 선 객체 관리 (clear용)
    private val currentPolylines = mutableListOf<Polyline>()

    /** [초기화] 지도 깨끗이 비우기 */
    fun clear() {
        currentPolylines.forEach { it.remove() }
        currentPolylines.clear()
        overlapLayer?.removeAll()
    }

    /**
     * [기본 선 그리기 (자동차/도보)]
     * ★ 변경점: 단일선이 아닌 '회색 테두리 + 사용자 색 심지'의 이중선으로 그립니다.
     */
    fun drawPolyline(points: List<LatLng>, userColor: Int) {
        drawDualLayerPolyline(
            points = points,
            borderColor = COLOR_GRAY_BORDER, // 테두리는 통일된 회색
            coreColor = userColor,           // 심지는 사용자 고유 색
            borderWidth = STD_BORDER_WIDTH,
            coreWidth = STD_CORE_WIDTH,
            targetLayer = routeLayer
        )
    }

    /**
     * [합류 구간 강조 (빨간선)]
     * ★ 변경점: '진한 빨강 테두리 + 밝은 빨강 심지'의 두꺼운 이중선으로 그립니다.
     */
    /**
     * [합류 구간 강조 (빨간선)]
     * ★ 수정됨: isTransitLeader 파라미터 추가
     * - True(대중교통): 테두리 없이 심지만 빨갛게 -> 기존 노선색 테두리가 보임
     * - False(자차/도보): 빨간 테두리 + 빨간 심지 -> 굵은 빨간선
     */
    fun drawRedLine(points: List<LatLng>, isTransitLeader: Boolean) {
        if (isTransitLeader) {
            // [대중교통 대장] 테두리 안 그림 (밑에 깔린 노선색 유지), 심지는 표준 두께
            drawDualLayerPolyline(
                points = points,
                borderColor = Color.TRANSPARENT, // 투명 테두리 (사실상 안 그림)
                coreColor = COLOR_RED_CORE,
                borderWidth = 0f,
                coreWidth = STD_CORE_WIDTH, // 9f (기존 심지와 동일 두께)
                targetLayer = overlapLayer
            )
        } else {
            // [자동차/도보 대장] 빨간 테두리 + 빨간 심지 (강조 두께)
            drawDualLayerPolyline(
                points = points,
                borderColor = COLOR_RED_BORDER,
                coreColor = COLOR_RED_CORE,
                borderWidth = EMPHASIS_BORDER_WIDTH,
                coreWidth = EMPHASIS_CORE_WIDTH,
                targetLayer = overlapLayer
            )
        }
    }

    /**
     * [대중교통 경로 자르기 & 그리기]
     * 기존 로직 유지. 단, 내부에서 호출하는 drawDualColorPolyline이 표준 두께를 사용하도록 수정됨.
     */
    fun drawTransitRouteCut(segments: List<TransitPathSegment>, cutLimitTotalIndex: Int, userColor: Int) {
        var currentIndex = 0
        for (seg in segments) {
            val segSize = seg.points.size
            if (segSize == 0) continue

            val pointsToDraw = if (currentIndex + segSize <= cutLimitTotalIndex) {
                seg.points // 전체 구간
            } else if (currentIndex < cutLimitTotalIndex) {
                seg.points.take(cutLimitTotalIndex - currentIndex + 1) // 잘린 구간
            } else {
                null // 안 그리는 구간
            }

            if (pointsToDraw != null) {
                drawDualLayerPolyline(
                    points = pointsToDraw,
                    borderColor = parseColorSafe(seg.color), // 노선 고유 색상
                    coreColor = userColor,
                    borderWidth = STD_BORDER_WIDTH,
                    coreWidth = STD_CORE_WIDTH,
                    targetLayer = routeLayer
                )
            }

            if (currentIndex + segSize >= cutLimitTotalIndex) break
            currentIndex += segSize
        }
    }

    /** [카메라 이동] */
    fun moveCameraToFit(points: List<LatLng>) {
        if (points.isEmpty()) return
        val cameraUpdate = com.kakao.vectormap.camera.CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 100) // padding 약간 증가
        kakaoMap.moveCamera(cameraUpdate)
    }

    // ========================================================================
    // [내부 헬퍼 함수] 실제 이중선을 그리는 핵심 로직
    // ========================================================================

    /**
     * [핵심] 이중 선(Dual Layer Polyline) 그리기 공통 함수
     * 두꺼운 테두리 선을 먼저 그리고, 그 위에 얇은 심지 선을 덧그립니다.
     */
    private fun drawDualLayerPolyline(
        points: List<LatLng>,
        borderColor: Int,
        coreColor: Int,
        borderWidth: Float,
        coreWidth: Float,
        targetLayer: com.kakao.vectormap.shape.ShapeLayer?
    ) {
        if (points.isEmpty() || targetLayer == null) return

        // 1. 테두리 그리기 (두껍게)
        val optsBorder = PolylineOptions.from(MapPoints.fromLatLng(points), borderWidth, borderColor)
        targetLayer.addPolyline(optsBorder)?.let { if (targetLayer == routeLayer) currentPolylines.add(it) }

        // 2. 심지 그리기 (얇게)
        val optsCore = PolylineOptions.from(MapPoints.fromLatLng(points), coreWidth, coreColor)
        targetLayer.addPolyline(optsCore)?.let { if (targetLayer == routeLayer) currentPolylines.add(it) }
    }

    // 색상 문자열 파싱 안전 장치
    private fun parseColorSafe(colorStr: String?): Int {
        return try {
            val c = colorStr?.trim() ?: return COLOR_DEFAULT_TRANSIT
            Color.parseColor(if (c.startsWith("#")) c else "#$c")
        } catch (e: Exception) { COLOR_DEFAULT_TRANSIT }
    }

    /**
     * ★ [수정] 내 경로만 집중해서 그리기 (Focus Mode)
     * - 인자 추가: redPoints (합류 후 경로)
     */
    fun drawFocusedRoute(
        myPoints: List<LatLng>,
        redPoints: List<LatLng>?,
        userColor: Int,
        isTransitLeader: Boolean = false // 빨간선 스타일 결정용 (대중교통 대장인지)
    ) {
        // 1. 기존 그림 싹 지우기
        clear()

        // 2. 내 경로(합류 전) 그리기 - 파란색
        drawPolyline(myPoints, userColor)

        // 3. 합류 후 경로(빨간색) 그리기 - 있다면
        if (redPoints != null && redPoints.isNotEmpty()) {
            drawRedLine(redPoints, isTransitLeader)
        }

        // 4. 카메라 이동 (내 경로 + 빨간 경로 모두 포함하도록)
        val allPoints = if (redPoints != null) myPoints + redPoints else myPoints
        moveCameraToFit(allPoints)
    }

    fun restorePaths(savedData: List<com.example.gachiga.data.MemberPathData>) {
        // 1. 기존 그림 지우기
        clear()

        val allPointsForCamera = mutableListOf<LatLng>()

        // 2. 저장된 선들 다시 그리기
        savedData.forEach { data ->
            // SimpleLatLng -> Kakao LatLng 변환
            val kakaoPoints = data.points.map { LatLng.from(it.lat, it.lng) }

            // 선 그리기
            drawPolyline(kakaoPoints, data.color)

            allPointsForCamera.addAll(kakaoPoints)
        }

        // 3. 카메라 조정
        if (allPointsForCamera.isNotEmpty()) {
            moveCameraToFit(allPointsForCamera)
        }
    }
}