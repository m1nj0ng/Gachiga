package com.miujong.gachiga10.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Tmap Geometry 공통 타입
 */
sealed class TmapGeometry {

    /**
     * LineString 구조 : "coordinates": [ [lon,lat], [lon,lat], ... ]
     */
    data class LineString(
        val coords: List<Coord>
    ) : TmapGeometry()

    /**
     * MultiLineString 구조:
     * "coordinates": [
     *      [ [lon,lat], [lon,lat], ... ],
     *      [ [lon,lat], [lon,lat], ... ]
     * ]
     */
    data class MultiLineString(
        val multiple: List<List<Coord>>
    ) : TmapGeometry()

    /**
     * lon(경도), lat(위도)
     */
    data class Coord(
        val lon: Double,
        val lat: Double
    )
}
