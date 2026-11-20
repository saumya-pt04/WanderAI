package com.wanderai.travelplanner

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.wanderai.travelplanner.databinding.ActivityCampMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NearbyPlace(
    val name: String,
    val type: String,
    val distanceText: String,
    val icon: String,
    val latLng: LatLng
)

class CampMapActivity : BaseNavigationActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityCampMapBinding
    private var googleMap: GoogleMap? = null
    private var mapLoadJob: Job? = null

    private var originName: String = ""
    private var destinationName: String = ""
    private var lastLoadedKey: String = ""

    override fun getSelectedNavigationItemId(): Int = R.id.nav_camp_map

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCampMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUniversalNavigation(
            binding.rootView,
            binding.topBar,
            binding.includeBottomNav.universalBottomNav
        )

        binding.rvNearbyStops.layoutManager = LinearLayoutManager(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        binding.btnOpenExternalNav.setOnClickListener {
            if (destinationName.isEmpty() && originName.isEmpty()) {
                Toast.makeText(this, "Enter a location on the Explore page first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val target = destinationName.ifEmpty { originName }
            val query = if (originName.isNotEmpty() && destinationName.isNotEmpty() &&
                !originName.equals(destinationName, ignoreCase = true)
            ) {
                "https://www.google.com/maps/dir/?api=1&origin=${URLEncoder.encode(originName, "UTF-8")}&destination=${URLEncoder.encode(destinationName, "UTF-8")}"
            } else {
                "geo:0,0?q=${URLEncoder.encode(target, "UTF-8")}"
            }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(query)))
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnQuickProfile.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }
    }

    override fun onResume() {
        super.onResume()
        syncLocationFromFirstPage()
        googleMap?.let { map ->
            val currentKey = "$originName|$destinationName"
            if (currentKey != lastLoadedKey) {
                loadLocationOnMap(map)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        syncLocationFromFirstPage()
        googleMap?.let { loadLocationOnMap(it) }
    }

    private fun isPredefinedDummyLocation(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val lower = value.trim().lowercase()
        return lower.contains("yellowstone") ||
                lower.contains("big sky") ||
                lower == "san francisco, ca" ||
                lower == "los angeles, ca"
    }

    private fun syncLocationFromFirstPage() {
        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)

        val savedOrigin = prefs.getString("LAST_ORIGIN", "").orEmpty()
        val savedDest = prefs.getString("LAST_DESTINATION", "").orEmpty()
        if (isPredefinedDummyLocation(savedOrigin) || isPredefinedDummyLocation(savedDest)) {
            prefs.edit().remove("LAST_ORIGIN").remove("LAST_DESTINATION").apply()
        }

        val intentOrigin = intent.getStringExtra(IntentNavigator.EXTRA_ORIGIN)
            ?.takeUnless { isPredefinedDummyLocation(it) }
        val intentDest = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION)
            ?.takeUnless { isPredefinedDummyLocation(it) }

        val cleanSavedOrigin = savedOrigin.takeUnless { isPredefinedDummyLocation(it) }.orEmpty()
        val cleanSavedDest = savedDest.takeUnless { isPredefinedDummyLocation(it) }.orEmpty()

        originName = (intentOrigin ?: cleanSavedOrigin).trim()
        destinationName = (intentDest ?: cleanSavedDest).trim()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        syncLocationFromFirstPage()
        loadLocationOnMap(map)
    }

    private fun loadLocationOnMap(map: GoogleMap) {
        lastLoadedKey = "$originName|$destinationName"
        mapLoadJob?.cancel()

        if (destinationName.isEmpty() && originName.isEmpty()) {
            map.clear()
            binding.tvMapTitle.text = "Your Destination / Location"
            binding.tvGpsCoordinates.text = "📍 Search a location on Explore page first"
            binding.rvNearbyStops.adapter = NearbyPlaceAdapter(emptyList()) {}
            return
        }

        val targetPlace = destinationName.ifEmpty { originName }
        binding.tvMapTitle.text = "$targetPlace Map 🗺️"
        binding.tvGpsCoordinates.text = "📍 Locating $targetPlace..."

        mapLoadJob = lifecycleScope.launch {
            val destCoords = if (destinationName.isNotEmpty()) resolveLocationCoordinates(destinationName) else null
            val originCoords = if (originName.isNotEmpty()) resolveLocationCoordinates(originName) else null

            val primaryCoords = destCoords ?: originCoords
            if (primaryCoords == null) {
                map.clear()
                binding.tvGpsCoordinates.text = "⚠️ Location not found: $targetPlace"
                binding.rvNearbyStops.adapter = NearbyPlaceAdapter(emptyList()) {}
                return@launch
            }

            val latDir = if (primaryCoords.latitude >= 0) "N" else "S"
            val lonDir = if (primaryCoords.longitude >= 0) "E" else "W"
            binding.tvGpsCoordinates.text = String.format(
                Locale.US,
                "📍 %.4f° %s, %.4f° %s",
                abs(primaryCoords.latitude), latDir,
                abs(primaryCoords.longitude), lonDir
            )

            map.clear()

            if (destCoords != null) {
                map.addMarker(
                    MarkerOptions()
                        .position(destCoords)
                        .title(destinationName)
                        .snippet("Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
                )?.showInfoWindow()
            }

            if (originCoords != null) {
                map.addMarker(
                    MarkerOptions()
                        .position(originCoords)
                        .title(originName)
                        .snippet("Starting Point")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
            }

            if (originCoords != null && destCoords != null && !originName.equals(destinationName, ignoreCase = true)) {
                map.addPolyline(
                    PolylineOptions()
                        .add(originCoords, destCoords)
                        .width(10f)
                        .color(Color.parseColor("#C69753"))
                )

                val bounds = LatLngBounds.Builder()
                    .include(originCoords)
                    .include(destCoords)
                    .build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 110))
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(primaryCoords, 12f))
            }

            val nearbyPlaces = fetchNearbyPlacesFromApi(primaryCoords)
            binding.rvNearbyStops.adapter = NearbyPlaceAdapter(nearbyPlaces) { selectedPlace ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedPlace.latLng, 15f))
                map.addMarker(
                    MarkerOptions()
                        .position(selectedPlace.latLng)
                        .title(selectedPlace.name)
                        .snippet(selectedPlace.type)
                )?.showInfoWindow()
            }
        }
    }

    private suspend fun resolveLocationCoordinates(placeName: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(this@CampMapActivity, Locale.getDefault())
                val latLng = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(placeName, 1) { addresses ->
                            val first = addresses.firstOrNull()
                            cont.resume(first?.let { LatLng(it.latitude, it.longitude) })
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(placeName, 1)?.firstOrNull()?.let {
                        LatLng(it.latitude, it.longitude)
                    }
                }
                if (latLng != null) return@withContext latLng
            }
        } catch (_: Exception) {
        }

        try {
            val encoded = URLEncoder.encode(placeName, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WanderAI-TravelPlanner-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(responseText)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    return@withContext LatLng(obj.getDouble("lat"), obj.getDouble("lon"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private suspend fun fetchNearbyPlacesFromApi(center: LatLng): List<NearbyPlace> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NearbyPlace>()
        try {
            val query = """
                [out:json][timeout:10];
                (
                  node["amenity"~"fuel|cafe|restaurant|charging_station"](around:5000,${center.latitude},${center.longitude});
                  node["tourism"~"hotel|attraction|viewpoint"](around:5000,${center.latitude},${center.longitude});
                );
                out body 8;
            """.trimIndent()

            val url = URL("https://overpass-api.de/api/interpreter?data=${URLEncoder.encode(query, "UTF-8")}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val elements = json.optJSONArray("elements") ?: JSONArray()

                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags") ?: continue
                    val name = tags.optString("name").ifEmpty { continue }
                    val lat = el.getDouble("lat")
                    val lon = el.getDouble("lon")
                    val amenity = tags.optString("amenity").ifEmpty { tags.optString("tourism", "Point of Interest") }

                    val distKm = calculateDistanceKm(center.latitude, center.longitude, lat, lon)
                    val (icon, label) = when (amenity) {
                        "fuel" -> "⛽" to "Fuel Station"
                        "charging_station" -> "⚡" to "EV Charging Hub"
                        "cafe", "restaurant" -> "☕" to "Dining & Refreshments"
                        "hotel" -> "🏨" to "Stay & Lodging"
                        "viewpoint", "attraction" -> "📸" to "Scenic Attraction"
                        else -> "📍" to amenity.replaceFirstChar { it.uppercase() }
                    }

                    results.add(
                        NearbyPlace(
                            name = name,
                            type = label,
                            distanceText = String.format(Locale.US, "%.1f km", distKm),
                            icon = icon,
                            latLng = LatLng(lat, lon)
                        )
                    )
                    if (results.size >= 5) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    inner class NearbyPlaceAdapter(
        private val places: List<NearbyPlace>,
        private val onPlaceClick: (NearbyPlace) -> Unit
    ) : RecyclerView.Adapter<NearbyPlaceAdapter.ViewHolder>() {

        inner class ViewHolder(
            val container: LinearLayout,
            val tvIcon: TextView,
            val tvName: TextView,
            val tvType: TextView,
            val tvDist: TextView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * d).toInt()

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_card_glass)
                elevation = dp(1).toFloat()
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            val tvIcon = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_input_field)
                gravity = Gravity.CENTER
                textSize = 18f
            }

            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(8)
                }
            }

            val tvName = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.text_high_emphasis))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            }

            val tvType = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
                textSize = 12f
            }

            textCol.addView(tvName)
            textCol.addView(tvType)

            val tvDist = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.brand_coral))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            }

            row.addView(tvIcon)
            row.addView(textCol)
            row.addView(tvDist)

            return ViewHolder(row, tvIcon, tvName, tvType, tvDist)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = places[position]
            holder.tvIcon.text = item.icon
            holder.tvName.text = item.name
            holder.tvType.text = item.type
            holder.tvDist.text = item.distanceText
            holder.container.setOnClickListener { onPlaceClick(item) }
        }

        override fun getItemCount(): Int = places.size
    }
}