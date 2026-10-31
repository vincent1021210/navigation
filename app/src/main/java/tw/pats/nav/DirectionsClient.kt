package tw.pats.nav

import com.google.android.gms.maps.model.LatLng
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class NavStep(
    val instruction: String,
    val end: LatLng,
    val distanceMeters: Int
)

data class RouteResult(
    val points: List<LatLng>,
    val steps: List<NavStep>,
    val durationText: String,
    val distanceText: String
)

class DirectionsClient(private val apiKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetch(origin: LatLng, dest: LatLng, mode: String): RouteResult {
        val o = "${origin.latitude},${origin.longitude}"
        val d = "${dest.latitude},${dest.longitude}"
        val url =
            "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${enc(o)}&destination=${enc(d)}" +
                "&mode=${enc(mode)}&language=zh-TW&key=${enc(apiKey)}"
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: throw IllegalStateException("empty")
            return parse(body)
        }
    }

    private fun parse(json: String): RouteResult {
        val root = JSONObject(json)
        val status = root.optString("status")
        if (status != "OK") throw IllegalStateException(status.ifBlank { "NO_ROUTE" })
        val route = root.getJSONArray("routes").getJSONObject(0)
        val overview = route.getJSONObject("overview_polyline").getString("points")
        val points = Geo.decodePolyline(overview)
        val leg = route.getJSONArray("legs").getJSONObject(0)
        val stepsJson = leg.getJSONArray("steps")
        val steps = ArrayList<NavStep>()
        for (i in 0 until stepsJson.length()) {
            val s = stepsJson.getJSONObject(i)
            val end = s.getJSONObject("end_location")
            steps.add(
                NavStep(
                    instruction = Geo.stripHtml(s.optString("html_instructions")),
                    end = LatLng(end.getDouble("lat"), end.getDouble("lng")),
                    distanceMeters = s.getJSONObject("distance").optInt("value")
                )
            )
        }
        return RouteResult(
            points = points,
            steps = steps,
            durationText = leg.getJSONObject("duration").optString("text"),
            distanceText = leg.getJSONObject("distance").optString("text")
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
