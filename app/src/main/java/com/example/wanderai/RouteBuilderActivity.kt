package com.wanderai.travelplanner

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.wanderai.travelplanner.databinding.ActivityRouteBuilderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RouteBuilderActivity : BaseNavigationActivity() {

    private lateinit var binding: ActivityRouteBuilderBinding
    private var calculationJob: Job? = null
    private var originSearchJob: Job? = null
    private var destSearchJob: Job? = null
    private var lastCalculatedResult: DynamicTripResult? = null

    private var selectedDays = 0
    private var selectedMembers = 0

    override fun getSelectedNavigationItemId(): Int = R.id.nav_route_builder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        binding = ActivityRouteBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUniversalNavigation(
            binding.rootView,
            binding.topBar,
            binding.includeBottomNav.universalBottomNav
        )

        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)

        // Purge any legacy hardcoded defaults
        val cachedOrigin = prefs.getString("LAST_ORIGIN", "").orEmpty()
        val cachedDest = prefs.getString("LAST_DESTINATION", "").orEmpty()
        if (cachedOrigin.equals("San Francisco, CA", ignoreCase = true) ||
            cachedDest.equals("Los Angeles, CA", ignoreCase = true) ||
            cachedDest.contains("Yellowstone", ignoreCase = true)
        ) {
            prefs.edit()
                .remove("LAST_ORIGIN")
                .remove("LAST_DESTINATION")
                .putInt("LAST_TOTAL_BUDGET_USD", 0)
                .apply()
        }

        val initialOrigin = intent.getStringExtra(IntentNavigator.EXTRA_ORIGIN)
            ?.takeUnless { it.equals("San Francisco, CA", ignoreCase = true) }
            .orEmpty()
        val initialDest = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION)
            ?.takeUnless { it.equals("Los Angeles, CA", ignoreCase = true) }
            .orEmpty()

        binding.etOrigin.setText(initialOrigin)
        binding.etDestination.setText(initialDest)
        binding.tvDaysCount.text = "0 Days"
        binding.tvMembersCount.text = "0 People"

        binding.etOrigin.threshold = 2
        binding.etDestination.threshold = 2

        fun saveCurrentState(origin: String, destination: String, totalBudgetUsd: Int) {
            prefs.edit()
                .putString("LAST_ORIGIN", origin)
                .putString("LAST_DESTINATION", destination)
                .putInt("LAST_DAYS", selectedDays)
                .putInt("LAST_MEMBERS", selectedMembers)
                .putInt("LAST_TOTAL_BUDGET_USD", totalBudgetUsd)
                .apply()
        }

        fun resetMetricsToZero() {
            calculationJob?.cancel()
            lastCalculatedResult = null
            binding.tvDistance.text = "0 mi"
            binding.tvDriveTime.text = "0h"
            binding.tvEstExpense.text = "₹0 ($0)"
            saveCurrentState(
                binding.etOrigin.text.toString().trim(),
                binding.etDestination.text.toString().trim(),
                0
            )
        }

        fun fetchLiveCalculations() {
            val origin = binding.etOrigin.text.toString().trim()
            val destination = binding.etDestination.text.toString().trim()

            if (origin.isEmpty() || destination.isEmpty()) {
                resetMetricsToZero()
                return
            }

            saveCurrentState(origin, destination, 0)

            calculationJob?.cancel()
            calculationJob = lifecycleScope.launch {
                delay(600)

                binding.tvEstExpense.text = "Estimating..."
                binding.tvDistance.text = "..."
                binding.tvDriveTime.text = "..."

                val effectiveDays = selectedDays.coerceAtLeast(1)
                val effectiveMembers = selectedMembers.coerceAtLeast(1)

                val result = RealWorldTravelEngine.calculateRealWorldTrip(
                    origin = origin,
                    destination = destination,
                    days = effectiveDays,
                    travelers = effectiveMembers
                )

                result.onSuccess { data ->
                    lastCalculatedResult = data
                    binding.tvDistance.text = "%.0f mi".format(data.realDistanceMiles)
                    binding.tvDriveTime.text = data.durationText

                    if (selectedDays == 0 || selectedMembers == 0) {
                        binding.tvEstExpense.text = "₹0 ($0)"
                        saveCurrentState(origin, destination, 0)
                    } else {
                        binding.tvEstExpense.text = data.formattedDualTotal
                        saveCurrentState(origin, destination, data.totalCostUsd)
                    }
                }.onFailure {
                    lastCalculatedResult = null
                    val localDist = AITravelEngine.estimateDistance(origin, destination)
                    binding.tvDistance.text = "$localDist mi"
                    binding.tvDriveTime.text = "${localDist / 50}h"

                    if (selectedDays == 0 || selectedMembers == 0) {
                        binding.tvEstExpense.text = "₹0 ($0)"
                        saveCurrentState(origin, destination, 0)
                    } else {
                        val localBudget = AITravelEngine.generateBudget(
                            destination = destination,
                            distanceMiles = localDist,
                            days = selectedDays,
                            travelers = selectedMembers
                        )
                        binding.tvEstExpense.text = AITravelEngine.formatDualCurrency(localBudget.totalBudget)
                        saveCurrentState(origin, destination, localBudget.totalBudget)
                    }
                }
            }
        }

        if (initialOrigin.isEmpty() || initialDest.isEmpty()) {
            resetMetricsToZero()
        } else {
            fetchLiveCalculations()
        }

        // Live Global Place Autocomplete
        binding.etOrigin.doAfterTextChanged { editable ->
            val query = editable?.toString()?.trim().orEmpty()
            fetchLiveCalculations()
            if (query.length >= 2) {
                originSearchJob?.cancel()
                originSearchJob = lifecycleScope.launch {
                    delay(350)
                    val matches = searchGlobalPlacesApi(query)
                    if (matches.isNotEmpty() && binding.etOrigin.hasFocus()) {
                        val adapter = ArrayAdapter(
                            this@RouteBuilderActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            matches
                        )
                        binding.etOrigin.setAdapter(adapter)
                        if (binding.etOrigin.windowToken != null) {
                            binding.etOrigin.showDropDown()
                        }
                    }
                }
            }
        }

        binding.etDestination.doAfterTextChanged { editable ->
            val query = editable?.toString()?.trim().orEmpty()
            fetchLiveCalculations()
            if (query.length >= 2) {
                destSearchJob?.cancel()
                destSearchJob = lifecycleScope.launch {
                    delay(350)
                    val matches = searchGlobalPlacesApi(query)
                    if (matches.isNotEmpty() && binding.etDestination.hasFocus()) {
                        val adapter = ArrayAdapter(
                            this@RouteBuilderActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            matches
                        )
                        binding.etDestination.setAdapter(adapter)
                        if (binding.etDestination.windowToken != null) {
                            binding.etDestination.showDropDown()
                        }
                    }
                }
            }
        }

        binding.etOrigin.setOnItemClickListener { _, _, _, _ -> fetchLiveCalculations() }
        binding.etDestination.setOnItemClickListener { _, _, _, _ -> fetchLiveCalculations() }

        // Days Picker Listeners
        binding.btnMinusDays.setOnClickListener {
            if (selectedDays > 0) {
                selectedDays--
                binding.tvDaysCount.text = if (selectedDays == 1) "1 Day" else "$selectedDays Days"
                fetchLiveCalculations()
            }
        }
        binding.btnPlusDays.setOnClickListener {
            if (selectedDays < 30) {
                selectedDays++
                binding.tvDaysCount.text = if (selectedDays == 1) "1 Day" else "$selectedDays Days"
                fetchLiveCalculations()
            }
        }

        // Members Picker Listeners
        binding.btnMinusMembers.setOnClickListener {
            if (selectedMembers > 0) {
                selectedMembers--
                binding.tvMembersCount.text = if (selectedMembers == 1) "1 Person" else "$selectedMembers People"
                fetchLiveCalculations()
            }
        }
        binding.btnPlusMembers.setOnClickListener {
            if (selectedMembers < 20) {
                selectedMembers++
                binding.tvMembersCount.text = if (selectedMembers == 1) "1 Person" else "$selectedMembers People"
                fetchLiveCalculations()
            }
        }

        binding.btnGeneratePlan.setOnClickListener {
            val origin = binding.etOrigin.text.toString().trim()
            val destination = binding.etDestination.text.toString().trim()

            if (origin.isEmpty() || destination.isEmpty()) {
                Toast.makeText(this, "Please enter both origin and destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedDays <= 0 || selectedMembers <= 0) {
                Toast.makeText(this, "Please select at least 1 day and 1 member", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result = lastCalculatedResult
            val finalBudget = result?.totalCostUsd ?: AITravelEngine.generateBudget(
                destination,
                AITravelEngine.estimateDistance(origin, destination),
                selectedDays,
                selectedMembers
            ).totalBudget

            saveCurrentState(origin, destination, finalBudget)

            val finalDistance = result?.realDistanceMiles?.toInt()
                ?: AITravelEngine.estimateDistance(origin, destination)

            val budgetIntent = Intent(this, ExpenseEstimatorActivity::class.java).apply {
                putExtra(IntentNavigator.EXTRA_ORIGIN, origin)
                putExtra(IntentNavigator.EXTRA_DESTINATION, destination)
                putExtra(IntentNavigator.EXTRA_BUDGET, finalBudget)
                putExtra(IntentNavigator.EXTRA_DISTANCE, finalDistance)
                putExtra("EXTRA_DAYS", selectedDays)
                putExtra("EXTRA_TRAVELERS", selectedMembers)
            }
            startActivity(budgetIntent)
        }

        binding.btnOpenAiGuide.setOnClickListener {
            val origin = binding.etOrigin.text.toString().trim()
            val destination = binding.etDestination.text.toString().trim()
            val currentBudget = lastCalculatedResult?.totalCostUsd ?: 0
            saveCurrentState(origin, destination, currentBudget)
            startActivity(Intent(this, AIChatActivity::class.java))
        }

        binding.btnCheckWeatherIntent.setOnClickListener {
            val destination = binding.etDestination.text.toString().trim()
            if (destination.isEmpty()) {
                Toast.makeText(this, "Enter a destination first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            IntentNavigator.navigateToWeatherMicroclimate(this, destination = destination)
        }

        binding.btnQuickProfile.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }
    }

    private suspend fun searchGlobalPlacesApi(query: String): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WanderAI-TravelPlanner-Android")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(body)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val displayName = obj.optString("display_name")
                    if (displayName.isNotEmpty()) {
                        val parts = displayName.split(",").map { it.trim() }
                        val cleanName = when {
                            parts.size >= 3 -> "${parts[0]}, ${parts[parts.size - 2]}, ${parts.last()}"
                            else -> displayName
                        }
                        if (!results.contains(cleanName)) {
                            results.add(cleanName)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        results
    }
}