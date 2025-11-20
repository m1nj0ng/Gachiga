package com.miujong.gachiga10.network

import com.google.gson.*
import java.lang.reflect.Type

class TmapGeometryDeserializer : JsonDeserializer<TmapGeometry> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): TmapGeometry {

        val obj = json.asJsonObject
        val type = obj["type"]?.asString ?: ""

        return when (type) {
            "LineString" -> {
                val arr = obj["coordinates"].asJsonArray
                TmapGeometry.LineString(parseCoords(arr))
            }

            "MultiLineString" -> {
                val outer = obj["coordinates"].asJsonArray
                val lines = outer.map { part ->
                    parseCoords(part.asJsonArray)
                }
                TmapGeometry.MultiLineString(lines)
            }

            else -> {
                // 기본적으로 빈 LineString 반환
                TmapGeometry.LineString(emptyList())
            }
        }
    }

    private fun parseCoords(arr: JsonArray): List<TmapGeometry.Coord> =
        arr.mapNotNull { item ->
            val pair = item.asJsonArray
            if (pair.size() >= 2) {
                val lon = pair[0].asDouble
                val lat = pair[1].asDouble
                TmapGeometry.Coord(lon, lat)
            } else null
        }
}
