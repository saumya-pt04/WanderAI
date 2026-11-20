package com.wanderai.travelplanner

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object AITravelEngine {

    const val USD_TO_INR_RATE = 86.50

    // Structured JSON model for Budget, Itinerary & Packing List
    private val geminiModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.4f
            }
        )
    }

    // Conversational AI Travel Guide Agent model
    private val chatModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are WanderAI, a friendly, concise, and expert travel guide assistant.
                    Help users plan routes, local food, hidden gems, safety tips, and packing advice.
                    Keep responses practical, formatted with short bullet points, and tailored to their current trip details.
                    """.trimIndent()
                )
            }
        )
    }

    fun createTripChatSession(
        origin: String,
        destination: String,
        days: Int,
        members: Int
    ): Chat {
        val tripContext = if (destination.isNotEmpty()) {
            "Current user trip context: Traveling from ${origin.ifEmpty { "their starting city" }} to $destination for $days days with $members travelers."
        } else {
            "The user hasn't picked a destination yet. Help them brainstorm great places to visit!"
        }

        return chatModel.startChat(
            history = listOf(
                content(role = "user") { text(tripContext) },
                content(role = "model") {
                    text(
                        if (destination.isNotEmpty()) {
                            "Got it! I'm ready to help you plan your $days-day trip to $destination for $members travelers. Ask me anything about spots, food, routes, or packing!"
                        } else {
                            "Hi! Where are you thinking of traveling next? Tell me your vibe, budget, or days, and I'll guide you!"
                        }
                    )
                }
            )
        )
    }

    fun formatDualCurrency(usdAmount: Int): String {
        val inrAmount = (usdAmount * USD_TO_INR_RATE).toLong()
        return "₹%,d ($%,d)".format(inrAmount, usdAmount)
    }

    // 1. AI-Generated Smart Budget Breakdown
    suspend fun generateSmartAIBudget(
        origin: String,
        destination: String,
        distanceMiles: Int,
        days: Int,
        travelers: Int
    ): TripBudgetResult = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Calculate a realistic travel budget from "$origin" to "$destination" ($distanceMiles miles) for $days days and $travelers travelers.
                Return ONLY a valid JSON object matching this exact schema:
                {
                  "totalUsd": 1500,
                  "categories": [
                    {"name": "Fuel & Transit", "subtitle": "Short detail", "usd": 300, "icon": "🚗"},
                    {"name": "Lodging & Stays", "subtitle": "Short detail", "usd": 600, "icon": "🏨"},
                    {"name": "Dining & Food", "subtitle": "Short detail", "usd": 350, "icon": "🍽️"},
                    {"name": "Activities & Passes", "subtitle": "Short detail", "usd": 150, "icon": "🎟️"},
                    {"name": "Contingency Buffer", "subtitle": "Short detail", "usd": 100, "icon": "🛡️"}
                  ]
                }
            """.trimIndent()

            val response = geminiModel.generateContent(prompt)
            val json = JSONObject(response.text ?: throw Exception("Empty AI response"))

            val totalUsd = json.getInt("totalUsd").coerceAtLeast(1)
            val catArray = json.getJSONArray("categories")
            val categories = mutableListOf<ExpenseCategory>()

            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                val usd = obj.getInt("usd")
                val pct = "${((usd.toDouble() / totalUsd) * 100).toInt()}%"
                categories.add(
                    ExpenseCategory(
                        name = obj.getString("name"),
                        subtitle = obj.getString("subtitle"),
                        cost = formatDualCurrency(usd),
                        percentage = pct,
                        icon = obj.getString("icon")
                    )
                )
            }
            TripBudgetResult(totalUsd, categories)
        } catch (e: Exception) {
            generateBudget(destination, distanceMiles, days, travelers)
        }
    }

    // 2. AI-Generated Day-by-Day Itinerary
    suspend fun generateAIItinerary(
        destination: String,
        days: Int
    ): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Create a $days-day travel itinerary for "$destination" with 4 key stops.
                Return ONLY a JSON array of objects:
                [{"time": "09:00 AM", "title": "Stop Name", "description": "Short 1-sentence tip"}]
            """.trimIndent()

            val response = geminiModel.generateContent(prompt)
            val array = JSONArray(response.text ?: "[]")
            val stops = mutableListOf<Triple<String, String, String>>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                stops.add(Triple(item.getString("time"), item.getString("title"), item.getString("description")))
            }
            stops
        } catch (e: Exception) {
            listOf(
                Triple("08:30 AM", "Morning Scenic Departure", "Start early to beat local traffic and enjoy clear views."),
                Triple("12:30 PM", "Local Culinary Stop", "Sample regional specialties in $destination."),
                Triple("03:30 PM", "Primary Landmark Exploration", "Guided walk and photography session."),
                Triple("07:30 PM", "Sunset Overlook & Dinner", "Unwind and review next day's route.")
            )
        }
    }

    // 3. AI-Generated Destination Packing List
    suspend fun generateAIPackingList(
        destination: String,
        days: Int
    ): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                List 6 essential packing items for a $days-day trip to "$destination" tailored to its weather and terrain.
                Return ONLY a JSON array:
                [{"item": "Item Name", "reason": "Why it's needed", "category": "Clothing"}]
            """.trimIndent()

            val response = geminiModel.generateContent(prompt)
            val array = JSONArray(response.text ?: "[]")
            val list = mutableListOf<Triple<String, String, String>>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Triple(obj.getString("item"), obj.getString("reason"), obj.getString("category")))
            }
            list
        } catch (e: Exception) {
            listOf(
                Triple("All-Weather Layer Jacket", "Essential for temperature shifts in $destination", "Clothing"),
                Triple("20,000mAh Power Bank", "Keep navigation and camera charged on the road", "Tech"),
                Triple("Reusable Insulated Flask", "Stay hydrated throughout transit", "Essentials")
            )
        }
    }

    // 4. AI-Generated Weather
    suspend fun generateWeather(
        destination: String
    ): TripWeatherResult = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Provide a microclimate weather forecast for "$destination".
                Return ONLY a JSON object matching this exact schema:
                {
                  "headline": "Short headline (e.g. Sunny and clear)",
                  "advisory": "A brief travel advisory or tip",
                  "isWarning": false,
                  "segments": [
                    {"timeSlot": "Morning", "condition": "Sunny", "temperature": "72°F", "icon": "☀️"},
                    {"timeSlot": "Afternoon", "condition": "Cloudy", "temperature": "75°F", "icon": "☁️"},
                    {"timeSlot": "Evening", "condition": "Rain", "temperature": "65°F", "icon": "🌧️"}
                  ]
                }
            """.trimIndent()

            val response = geminiModel.generateContent(prompt)
            val json = JSONObject(response.text ?: throw Exception("Empty AI response"))

            val segmentsArray = json.getJSONArray("segments")
            val segments = mutableListOf<WeatherSegment>()
            for (i in 0 until segmentsArray.length()) {
                val obj = segmentsArray.getJSONObject(i)
                segments.add(
                    WeatherSegment(
                        timeSlot = obj.getString("timeSlot"),
                        condition = obj.getString("condition"),
                        temperature = obj.getString("temperature"),
                        icon = obj.getString("icon")
                    )
                )
            }
            TripWeatherResult(
                headline = json.getString("headline"),
                advisory = json.getString("advisory"),
                isWarning = json.getBoolean("isWarning"),
                segments = segments
            )
        } catch (e: Exception) {
            TripWeatherResult(
                headline = "Clear Skies Expected",
                advisory = "Standard travel conditions for $destination.",
                isWarning = false,
                segments = listOf(
                    WeatherSegment("Morning", "Clear", "65°F", "☀️"),
                    WeatherSegment("Afternoon", "Sunny", "78°F", "☀️"),
                    WeatherSegment("Evening", "Breezy", "68°F", "🌙")
                )
            )
        }
    }

    // Local Offline Fallback Engine
    fun estimateDistance(origin: String, destination: String): Int {
        return 160 + (abs((origin.lowercase().trim() + destination.lowercase().trim()).hashCode()) % 650)
    }

    fun generateBudget(destination: String, distanceMiles: Int, days: Int = 4, travelers: Int = 3): TripBudgetResult {
        val fuelCost = ((distanceMiles / 28.0) * 3.60).toInt()
        val transitTotal = fuelCost + (days * 18)
        val lodging = 110 * days
        val food = 40 * days * travelers
        val activities = 25 * days * travelers
        val buffer = ((transitTotal + lodging + food + activities) * 0.08).toInt()
        val grandTotal = transitTotal + lodging + food + activities + buffer

        fun pct(value: Int): String = "${((value.toDouble() / grandTotal) * 100).toInt()}%"

        val categories = listOf(
            ExpenseCategory("Transit & Fuel", "$distanceMiles mi route + parking", formatDualCurrency(transitTotal), pct(transitTotal), "🚗"),
            ExpenseCategory("Lodging & Stays", "$days nights in $destination", formatDualCurrency(lodging), pct(lodging), "🏨"),
            ExpenseCategory("Food & Dining", "Meals for $travelers travelers", formatDualCurrency(food), pct(food), "🍽️"),
            ExpenseCategory("Activities & Passes", "Sightseeing & entry tickets", formatDualCurrency(activities), pct(activities), "🎟️"),
            ExpenseCategory("Contingency Buffer", "Unplanned rerouting & reserves", formatDualCurrency(buffer), pct(buffer), "🛡️")
        )

        return TripBudgetResult(grandTotal, categories)
    }
}