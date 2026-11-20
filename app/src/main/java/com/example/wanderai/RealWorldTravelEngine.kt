package com.wanderai.travelplanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

data class DynamicTripResult(
    val realDistanceMiles: Double,
    val realDistanceKm: Double,
    val durationText: String,
    val totalCostUsd: Int,
    val totalCostInr: Long,
    val formattedDualTotal: String,
    val categories: List<ExpenseCategory>
)

object RealWorldTravelEngine {

    const val USD_TO_INR_RATE = 86.50

    private val api: GoogleMapsApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleMapsApiService::class.java)
    }

    suspend fun calculateRealWorldTrip(
        origin: String,
        destination: String,
        days: Int = 1,
        travelers: Int = 1
    ): Result<DynamicTripResult> = withContext(Dispatchers.IO) {
        try {
            val cleanOrigin = origin.trim()
            val cleanDest = destination.trim()

            // 1. Try Google Distance Matrix first, then fall back to real OSRM road routing
            val routePair = fetchGoogleRoute(cleanOrigin, cleanDest)
                ?: fetchOsrmRealRoadRoute(cleanOrigin, cleanDest)
                ?: return@withContext Result.failure(Exception("Could not find a valid driving route between locations."))

            val distanceKm = routePair.first
            val distanceMiles = distanceKm * 0.621371
            val durationText = routePair.second

            val safeDays = days.coerceAtLeast(1)
            val safeTravelers = travelers.coerceAtLeast(1)
            val roomsNeeded = ceil(safeTravelers / 2.0).toInt().coerceAtLeast(1)

            // Detect if trip is within India for realistic local pricing
            val isIndiaTrip = isIndianLocation(cleanOrigin) && isIndianLocation(cleanDest)

            val totalTransitInr: Int
            val lodgingInr: Int
            val foodInr: Int
            val activitiesInr: Int
            val contingencyInr: Int

            if (isIndiaTrip) {
                // Realistic Indian travel rates (INR):
                // Fuel (~16 km/l @ ₹96/L = ₹6/km) + modest highway toll/parking
                val fuelInr = (distanceKm * 6.0).roundToInt().coerceAtLeast(120)
                val tollParkingInr = if (distanceKm > 40) ((distanceKm * 0.8).roundToInt() + (safeDays * 100)) else (safeDays * 50)
                totalTransitInr = fuelInr + tollParkingInr

                // Same-day short trip (< 150 km and 1 day) needs 0 hotel nights
                val nights = if (safeDays == 1 && distanceKm < 150.0) 0 else safeDays
                lodgingInr = nights * roomsNeeded * 2200

                // Realistic Indian dining (~₹650/person/day)
                foodInr = safeDays * safeTravelers * 650

                // Local sightseeing / entry tickets (~₹350/person/day)
                activitiesInr = safeDays * safeTravelers * 350

                val subtotalInr = totalTransitInr + lodgingInr + foodInr + activitiesInr
                contingencyInr = (subtotalInr * 0.05).roundToInt()
            } else {
                // Realistic International travel rates (converted to INR for unified math)
                val fuelUsd = ((distanceMiles / 28.0) * 3.60).roundToInt()
                val tollUsd = ((distanceMiles * 0.03) + (safeDays * 12)).roundToInt()
                totalTransitInr = ((fuelUsd + tollUsd) * USD_TO_INR_RATE).roundToInt()

                val nights = if (safeDays == 1 && distanceKm < 150.0) 0 else safeDays
                lodgingInr = (nights * roomsNeeded * 95 * USD_TO_INR_RATE).roundToInt()
                foodInr = (safeDays * safeTravelers * 35 * USD_TO_INR_RATE).roundToInt()
                activitiesInr = (safeDays * safeTravelers * 20 * USD_TO_INR_RATE).roundToInt()

                val subtotalInr = totalTransitInr + lodgingInr + foodInr + activitiesInr
                contingencyInr = (subtotalInr * 0.07).roundToInt()
            }

            val totalCostInr = (totalTransitInr + lodgingInr + foodInr + activitiesInr + contingencyInr).toLong()
            val totalCostUsd = (totalCostInr / USD_TO_INR_RATE).roundToInt().coerceAtLeast(1)

            fun fmtDualFromInr(inrVal: Int): String {
                val usdVal = (inrVal / USD_TO_INR_RATE).roundToInt()
                return "₹%,d ($%,d)".format(inrVal, usdVal)
            }

            fun pct(partInr: Int): String {
                if (totalCostInr <= 0) return "0%"
                return "${((partInr.toDouble() / totalCostInr) * 100).roundToInt()}%"
            }

            val nightLabel = if (lodgingInr == 0) "Same-day trip (no overnight stay)" else "$safeDays day(s) • $roomsNeeded room(s)"

            val categories = listOf(
                ExpenseCategory(
                    "Fuel & Transit",
                    "%.0f km (%.0f mi) route + tolls/parking".format(distanceKm, distanceMiles),
                    fmtDualFromInr(totalTransitInr),
                    pct(totalTransitInr),
                    "⛽"
                ),
                ExpenseCategory(
                    "Lodging & Hotels",
                    nightLabel,
                    fmtDualFromInr(lodgingInr),
                    pct(lodgingInr),
                    "🏨"
                ),
                ExpenseCategory(
                    "Dining & Food",
                    "Daily meals for $safeTravelers traveler(s)",
                    fmtDualFromInr(foodInr),
                    pct(foodInr),
                    "🍽️"
                ),
                ExpenseCategory(
                    "Activities & Entry",
                    "Local attractions & entry passes",
                    fmtDualFromInr(activitiesInr),
                    pct(activitiesInr),
                    "🎟️"
                ),
                ExpenseCategory(
                    "Contingency Buffer",
                    "Emergency & local buffer reserve",
                    fmtDualFromInr(contingencyInr),
                    pct(contingencyInr),
                    "🛡️"
                )
            )

            val formattedTotal = "₹%,d ($%,d)".format(totalCostInr, totalCostUsd)

            Result.success(
                DynamicTripResult(
                    realDistanceMiles = distanceMiles,
                    realDistanceKm = distanceKm,
                    durationText = durationText,
                    totalCostUsd = totalCostUsd,
                    totalCostInr = totalCostInr,
                    formattedDualTotal = formattedTotal,
                    categories = categories
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun fetchGoogleRoute(origin: String, destination: String): Pair<Double, String>? {
        return try {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isBlank()) return null

            val response = api.getDistanceMatrix(
                origins = origin,
                destinations = destination,
                mode = "driving",
                units = "metric",
                apiKey = apiKey
            )
            if (!response.isSuccessful || response.body() == null) return null
            val element = response.body()!!.rows?.firstOrNull()?.elements?.firstOrNull() ?: return null
            if (element.status != "OK" || element.distance == null || element.duration == null) return null

            val km = element.distance.value / 1000.0
            Pair(km, element.duration.text)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchOsrmRealRoadRoute(origin: String, destination: String): Pair<Double, String>? {
        return try {
            val startCoords = geocodeNominatim(origin) ?: return null
            val endCoords = geocodeNominatim(destination) ?: return null

            val routeUrl = URL(
                "https://router.project-osrm.org/route/v1/driving/" +
                        "${startCoords.second},${startCoords.first};${endCoords.second},${endCoords.first}?overview=false"
            )
            val conn = (routeUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WanderAI-TravelPlanner-Android")
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode != 200) return null

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val routes = json.optJSONArray("routes") ?: return null
            if (routes.length() == 0) return null

            val bestRoute = routes.getJSONObject(0)
            val distanceMeters = bestRoute.getDouble("distance")
            val durationSeconds = bestRoute.getDouble("duration")

            val km = distanceMeters / 1000.0
            val totalMinutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            val durationFormatted = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "${mins}m"
            }

            Pair(km, durationFormatted)
        } catch (_: Exception) {
            null
        }
    }

    private fun geocodeNominatim(place: String): Pair<Double, Double>? {
        return try {
            val encoded = URLEncoder.encode(place, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WanderAI-TravelPlanner-Android")
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode != 200) return null
            val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (array.length() == 0) return null
            val obj = array.getJSONObject(0)
            Pair(obj.getDouble("lat"), obj.getDouble("lon"))
        } catch (_: Exception) {
            null
        }
    }

    private fun isIndianLocation(location: String): Boolean {
        val lower = location.lowercase()
        val indianKeywords = listOf(
            "india", "gujarat", "maharashtra", "rajasthan", "delhi", "mumbai",
            "bengaluru", "bangalore", "chennai", "kolkata", "hyderabad", "pune",
            "ahmedabad", "surat", "vadodara", "rajkot", "wankaner", "jamnagar",
            "bhavnagar", "junagadh", "morbi", "gandhinagar", "kutch", "goa", "kerala"
        )
        return indianKeywords.any { lower.contains(it) } || Regex("\\b3[6-9]\\d{4}\\b").containsMatchIn(lower)
    }
}