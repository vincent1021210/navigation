package tw.pats.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import tw.pats.nav.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var map: GoogleMap? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val directions by lazy { DirectionsClient(BuildConfig.MAPS_API_KEY) }

    private var dest: LatLng? = null
    private var destName: String = ""
    private var lastLocation: LatLng? = null
    private var route: RouteResult? = null
    private var polyline: Polyline? = null
    private var navigating = false
    private var lastRerouteAt = 0L
    private var lastSpoken = ""
    private var tts: TextToSpeech? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) onLocationReady()
        else toast(getString(R.string.need_location))
    }

    private val placeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val place = Autocomplete.getPlaceFromIntent(result.data!!)
        dest = place.latLng
        destName = place.name ?: place.address ?: getString(R.string.search_hint)
        binding.searchButton.text = destName
        binding.searchButton.setTextColor(0xFF0F172A.toInt())
        dest?.let { d ->
            map?.addMarker(MarkerOptions().position(d).title(destName))
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(d, 15f))
            planRoute(fitCamera = true)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val here = LatLng(loc.latitude, loc.longitude)
            lastLocation = here
            if (navigating) {
                map?.animateCamera(CameraUpdateFactory.newLatLng(here))
                updateGuidance(here)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.MAPS_API_KEY.isBlank()) {
            toast(getString(R.string.need_key))
        } else if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.TAIWAN
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.searchButton.setOnClickListener { openSearch() }
        binding.navButton.setOnClickListener { toggleNav() }
        binding.modeGroup.addOnButtonCheckedListener { _, _, _ ->
            if (dest != null) planRoute(fitCamera = true)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        requestLocation()
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            onLocationReady()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun onLocationReady() {
        val map = map ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        map.isMyLocationEnabled = true
        lifecycleScope.launch {
            try {
                val loc = fused.lastLocation.await()
                if (loc != null) {
                    lastLocation = LatLng(loc.latitude, loc.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLocation!!, 16f))
                }
            } catch (_: Exception) { /* 沒有最後位置時等更新 */ }
        }
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(800L)
            .build()
        fused.requestLocationUpdates(req, locationCallback, mainLooper)
    }

    private fun openSearch() {
        if (BuildConfig.MAPS_API_KEY.isBlank()) {
            toast(getString(R.string.need_key))
            return
        }
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountries(listOf("TW"))
            .build(this)
        placeLauncher.launch(intent)
    }

    private fun travelMode(): String {
        return if (binding.modeGroup.checkedButtonId == R.id.modeWalk) "walking" else "driving"
    }

    private fun planRoute(fitCamera: Boolean, fromReroute: Boolean = false) {
        val origin = lastLocation
        val destination = dest
        if (origin == null) {
            toast(getString(R.string.need_location))
            return
        }
        if (destination == null) {
            toast(getString(R.string.pick_dest))
            return
        }
        if (BuildConfig.MAPS_API_KEY.isBlank()) {
            toast(getString(R.string.need_key))
            return
        }
        binding.navButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    directions.fetch(origin, destination, travelMode())
                }
                route = result
                drawRoute(result, fitCamera)
                binding.eta.text = "${result.durationText} · ${result.distanceText}"
                val first = result.steps.firstOrNull()?.instruction ?: destName
                binding.instruction.text = first
                if (fromReroute) speak("已重新規劃路線")
            } catch (e: Exception) {
                toast(getString(R.string.route_fail) + " (${e.message})")
            } finally {
                binding.navButton.isEnabled = true
            }
        }
    }

    private fun drawRoute(result: RouteResult, fitCamera: Boolean) {
        val map = map ?: return
        polyline?.remove()
        polyline = map.addPolyline(
            PolylineOptions()
                .addAll(result.points)
                .color(0xFF2563EB.toInt())
                .width(14f)
        )
        if (fitCamera && result.points.isNotEmpty()) {
            val b = LatLngBounds.builder()
            result.points.forEach { b.include(it) }
            lastLocation?.let { b.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120))
        }
    }

    private fun toggleNav() {
        if (navigating) {
            navigating = false
            binding.navButton.text = getString(R.string.start_nav)
            binding.instruction.text = destName.ifBlank { getString(R.string.idle_hint) }
            return
        }
        if (route == null) {
            planRoute(fitCamera = true)
        }
        if (lastLocation == null) {
            toast(getString(R.string.need_location))
            return
        }
        if (dest == null) {
            toast(getString(R.string.pick_dest))
            return
        }
        navigating = true
        binding.navButton.text = getString(R.string.stop_nav)
        lastLocation?.let { updateGuidance(it) }
    }

    private fun updateGuidance(here: LatLng) {
        val current = route ?: return
        val off = Geo.distanceToPolyline(here, current.points)
        val now = System.currentTimeMillis()
        if (off > 40 && now - lastRerouteAt > 12_000) {
            lastRerouteAt = now
            planRoute(fitCamera = false, fromReroute = true)
            return
        }
        val next = current.steps.minByOrNull { Geo.distanceMeters(here, it.end) } ?: return
        val remain = Geo.distanceMeters(here, next.end)
        val text = if (remain < 30) next.instruction else "約 ${remain.toInt()} 公尺後 ${next.instruction}"
        binding.instruction.text = text
        binding.eta.text = "${current.durationText} · ${current.distanceText}"
        if (remain < 30 && next.instruction != lastSpoken) {
            lastSpoken = next.instruction
            speak(next.instruction)
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(locationCallback)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
