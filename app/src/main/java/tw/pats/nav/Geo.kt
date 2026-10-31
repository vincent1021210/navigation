package tw.pats.nav

import com.google.android.gms.maps.model.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    fun distanceToPolyline(point: LatLng, line: List<LatLng>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return distanceMeters(point, line[0])
        var best = Double.MAX_VALUE
        for (i in 0 until line.lastIndex) {
            best = min(best, distanceToSegment(point, line[i], line[i + 1]))
        }
        return best
    }

    private fun distanceToSegment(p: LatLng, a: LatLng, b: LatLng): Double {
        val ax = a.longitude
        val ay = a.latitude
        val bx = b.longitude
        val by = b.latitude
        val px = p.longitude
        val py = p.latitude
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return distanceMeters(p, a)
        val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(0.0, 1.0)
        return distanceMeters(p, LatLng(ay + clamped * dy, ax + clamped * dx))
    }

    fun decodePolyline(encoded: String): List<LatLng> {
        val path = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            path.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return path
    }

    fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
