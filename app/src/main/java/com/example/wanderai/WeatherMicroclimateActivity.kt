package com.wanderai.travelplanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherMicroclimateActivity : BaseNavigationActivity() {

    private var morningTempView: TextView? = null
    private var afternoonTempView: TextView? = null
    private var eveningTempView: TextView? = null

    private var morningCondView: TextView? = null
    private var afternoonCondView: TextView? = null
    private var eveningCondView: TextView? = null

    private var headlineView: TextView? = null
    private var summarySubView: TextView? = null

    override fun getSelectedNavigationItemId(): Int = R.id.nav_route_builder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_microclimate)

        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup

        val topBar = findViewById<View>(R.id.topBar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.universalBottomNav)
        if (topBar != null && bottomNav != null) {
            setupUniversalNavigation(rootView, topBar, bottomNav)
        }

        findViewById<View>(R.id.btnQuickProfile)?.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }

        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)
        val destination = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION)
            ?.takeIf { it.isNotBlank() }
            ?: prefs.getString("LAST_DESTINATION", "Surat, 395008, India")
            ?: "Surat, 395008, India"

        bindExistingWeatherViews(rootView)

        applyWeatherToUi(
            destination = destination,
            summaryHeadline = "Clear Skies Expected",
            morningTempC = 18.0,
            morningCond = "☀️ Clear",
            afternoonTempC = 25.5,
            afternoonCond = "☀️ Sunny",
            eveningTempC = 20.0,
            eveningCond = "🌙 Breezy"
        )

        lifecycleScope.launch {
            val live = fetchLiveWeatherDualUnits(destination)
            if (live != null) {
                applyWeatherToUi(
                    destination = destination,
                    summaryHeadline = live.headline,
                    morningTempC = live.morningC,
                    morningCond = live.morningCond,
                    afternoonTempC = live.afternoonC,
                    afternoonCond = live.afternoonCond,
                    eveningTempC = live.eveningC,
                    eveningCond = live.eveningCond
                )
            }
        }
    }

    private fun bindExistingWeatherViews(root: ViewGroup) {
        val allTextViews = mutableListOf<TextView>()
        val allButtons = mutableListOf<Button>()

        fun collect(view: View) {
            when (view) {
                is Button -> allButtons.add(view)
                is TextView -> allTextViews.add(view)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collect(view.getChildAt(i))
                }
            }
        }
        collect(root)

        allButtons.firstOrNull { it.text.toString().contains("Adjust Route", ignoreCase = true) }
            ?.setOnClickListener {
                startActivity(Intent(this, RouteBuilderActivity::class.java))
                finish()
            }

        for (tv in allTextViews) {
            val txt = tv.text.toString().trim()
            when {
                txt.contains("Clear Skies", ignoreCase = true) -> headlineView = tv
                txt.startsWith("Standard travel conditions", ignoreCase = true) -> summarySubView = tv
                txt == "65°F" || txt.contains("65") -> morningTempView = tv
                txt == "78°F" || txt.contains("78") -> afternoonTempView = tv
                txt == "68°F" || txt.contains("68") -> eveningTempView = tv
                txt.contains("Clear") && morningCondView == null && !txt.contains("Skies") && !txt.contains("Route") -> morningCondView = tv
                txt.contains("Sunny") && afternoonCondView == null -> afternoonCondView = tv
                txt.contains("Breezy") && eveningCondView == null -> eveningCondView = tv
            }
        }
    }

    private fun formatDualTemp(celsius: Double): String {
        val cRounded = celsius.roundToInt()
        val fRounded = ((celsius * 9.0 / 5.0) + 32.0).roundToInt()
        return "${cRounded}°C (${fRounded}°F)"
    }

    private fun applyWeatherToUi(
        destination: String,
        summaryHeadline: String,
        morningTempC: Double,
        morningCond: String,
        afternoonTempC: Double,
        afternoonCond: String,
        eveningTempC: Double,
        eveningCond: String
    ) {
        headlineView?.text = summaryHeadline
        summarySubView?.text =
            "Standard travel conditions for $destination • Peak ${formatDualTemp(afternoonTempC)}."

        morningTempView?.text = formatDualTemp(morningTempC)
        morningCondView?.text = morningCond

        afternoonTempView?.text = formatDualTemp(afternoonTempC)
        afternoonCondView?.text = afternoonCond

        eveningTempView?.text = formatDualTemp(eveningTempC)
        eveningCondView?.text = eveningCond
    }

    private data class LiveWeatherSnapshot(
        val headline: String,
        val morningC: Double,
        val morningCond: String,
        val afternoonC: Double,
        val afternoonCond: String,
        val eveningC: Double,
        val eveningCond: String
    )

    private suspend fun fetchLiveWeatherDualUnits(place: String): LiveWeatherSnapshot? =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(place, "UTF-8")
                val geoUrl = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
                val geoConn = (geoUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "WanderAI-TravelPlanner-Android")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (geoConn.responseCode != 200) return@withContext null
                val geoArr = JSONArray(geoConn.inputStream.bufferedReader().use { it.readText() })
                if (geoArr.length() == 0) return@withContext null
                val lat = geoArr.getJSONObject(0).getDouble("lat")
                val lon = geoArr.getJSONObject(0).getDouble("lon")

                val weatherUrl = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                            "&hourly=temperature_2m,weathercode&forecast_days=1&timezone=auto"
                )
                val wConn = (weatherUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (wConn.responseCode != 200) return@withContext null

                val json = JSONObject(wConn.inputStream.bufferedReader().use { it.readText() })
                val hourly = json.getJSONObject("hourly")
                val temps = hourly.getJSONArray("temperature_2m")
                val codes = hourly.getJSONArray("weathercode")

                if (temps.length() < 21) return@withContext null

                val morningC = temps.getDouble(8)     // 08:00 AM
                val afternoonC = temps.getDouble(14)  // 02:00 PM
                val eveningC = temps.getDouble(20)    // 08:00 PM
                val afternoonCode = codes.getInt(14)

                LiveWeatherSnapshot(
                    headline = weatherHeadlineFromCode(afternoonCode),
                    morningC = morningC,
                    morningCond = weatherLabelFromCode(codes.getInt(8), isEvening = false),
                    afternoonC = afternoonC,
                    afternoonCond = weatherLabelFromCode(afternoonCode, isEvening = false),
                    eveningC = eveningC,
                    eveningCond = weatherLabelFromCode(codes.getInt(20), isEvening = true)
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun weatherLabelFromCode(code: Int, isEvening: Boolean): String = when (code) {
        0 -> if (isEvening) "🌙 Clear Night" else "☀️ Clear"
        1, 2 -> if (isEvening) "🌙 Partly Cloudy" else "🌤️ Sunny"
        3 -> "☁️ Overcast"
        45, 48 -> "🌫️ Misty"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "🌧️ Light Rain"
        95, 96, 99 -> "⛈️ Stormy"
        else -> if (isEvening) "🌙 Breezy" else "☀️ Pleasant"
    }

    private fun weatherHeadlineFromCode(code: Int): String = when (code) {
        0, 1, 2 -> "Clear Skies Expected"
        3, 45, 48 -> "Mild & Partly Cloudy"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Carry Rain Gear on Route"
        95, 96, 99 -> "Thunderstorm Advisory"
        else -> "Optimal Route Conditions"
    }
}