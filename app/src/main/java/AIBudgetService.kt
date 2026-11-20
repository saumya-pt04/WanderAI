package com.wanderai.travelplanner

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// 2. Real AI Service calling Google Gemini
class AIBudgetService(apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun calculateBudget(
        destination: String,
        distanceMiles: Int,
        days: Int = 4,
        travelers: Int = 2
    ): TripBudgetResult = withContext(Dispatchers.IO) {
        val prompt = """
            You are an expert travel budget planner AI.
            Calculate a detailed realistic expense budget for:
            - Destination: $destination
            - Approximate Travel Distance: $distanceMiles miles
            - Duration: $days days
            - Number of Travelers: $travelers

            Factor in:
            1. Transit/Fuel based strictly on the distance of $distanceMiles miles.
            2. Lodging, food, and activities based on the cost of living in $destination.

            Respond ONLY with a valid JSON object without markdown formatting:
            {
              "totalBudget": 1850,
              "currency": "USD",
              "categories": [
                {
                  "name": "Transit & Fuel",
                  "subtitle": "Fuel for $distanceMiles miles + tolls",
                  "cost": "$320",
                  "percentage": "17%",
                  "icon": "🚗"
                },
                {
                  "name": "Lodging & Stays",
                  "subtitle": "$days nights stay in $destination",
                  "cost": "$850",
                  "percentage": "46%",
                  "icon": "🏨"
                },
                {
                  "name": "Dining & Food",
                  "subtitle": "Daily meals for $travelers people",
                  "cost": "$420",
                  "percentage": "23%",
                  "icon": "🍽️"
                },
                {
                  "name": "Activities & Entry",
                  "subtitle": "Local tours and sightseeing",
                  "cost": "$160",
                  "percentage": "9%",
                  "icon": "🎟️"
                },
                {
                  "name": "Emergency Buffer",
                  "subtitle": "Contingency reserve",
                  "cost": "$100",
                  "percentage": "5%",
                  "icon": "🛡️"
                }
              ]
            }
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text
                ?.replace("```json", "")
                ?.replace("```", "")
                ?.trim() ?: ""

            parseBudgetJson(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            // Automatic fallback if device is offline or API call fails
            FallbackBudgetCalculator.calculate(destination, distanceMiles, days, travelers)
        }
    }

    private fun parseBudgetJson(jsonString: String): TripBudgetResult {
        val json = JSONObject(jsonString)
        val total = json.getInt("totalBudget")
        val categoryArray = json.getJSONArray("categories")
        val categories = mutableListOf<ExpenseCategory>()

        for (i in 0 until categoryArray.length()) {
            val item = categoryArray.getJSONObject(i)
            categories.add(
                ExpenseCategory(
                    name = item.getString("name"),
                    subtitle = item.getString("subtitle"),
                    cost = item.getString("cost"),
                    percentage = item.getString("percentage"),
                    icon = item.getString("icon")
                )
            )
        }
        return TripBudgetResult(total, categories)
    }
}

// 3. Fallback Calculator used when offline or network fails
object FallbackBudgetCalculator {
    fun calculate(
        destination: String,
        distanceMiles: Int,
        days: Int = 4,
        travelers: Int = 2
    ): TripBudgetResult {
        val averageMpg = 28.0
        val gasPricePerGallon = 3.85
        val fuelCost = ((distanceMiles / averageMpg) * gasPricePerGallon).toInt()
        val tollsAndParking = days * 20
        val totalTransit = fuelCost + tollsAndParking

        val destLower = destination.lowercase()
        val tierMultiplier = when {
            destLower.contains("paris") || destLower.contains("tokyo") ||
                    destLower.contains("new york") || destLower.contains("san francisco") -> 1.6
            destLower.contains("los angeles") || destLower.contains("big sur") ||
                    destLower.contains("chicago") || destLower.contains("miami") -> 1.3
            else -> 1.0
        }

        val totalLodging = (120 * tierMultiplier * days).toInt()
        val totalFood = (50 * tierMultiplier * days * travelers).toInt()
        val totalActivities = (30 * tierMultiplier * days).toInt()
        val contingency = ((totalTransit + totalLodging + totalFood + totalActivities) * 0.08).toInt()
        val total = totalTransit + totalLodging + totalFood + totalActivities + contingency

        fun pct(cost: Int) = "${((cost.toDouble() / total) * 100).toInt()}%"

        val categories = listOf(
            ExpenseCategory("Transit & Fuel", "$distanceMiles mi @ $3.85/gal + tolls", "$$totalTransit", pct(totalTransit), "🚗"),
            ExpenseCategory("Lodging & Stays", "$days nights in $destination", "$$totalLodging", pct(totalLodging), "🏨"),
            ExpenseCategory("Food & Dining", "Meals for $travelers travelers ($days days)", "$$totalFood", pct(totalFood), "🍽️"),
            ExpenseCategory("Activities & Entry", "Sightseeing & attractions", "$$totalActivities", pct(totalActivities), "🎟️"),
            ExpenseCategory("Contingency Buffer", "Unplanned / weather reserves", "$$contingency", pct(contingency), "🛡️")
        )

        return TripBudgetResult(total, categories)
    }
}