package com.wanderai.travelplanner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanderai.travelplanner.databinding.ActivityExpenseEstimatorBinding
import kotlinx.coroutines.launch

class ExpenseEstimatorActivity : BaseNavigationActivity() {

    private lateinit var binding: ActivityExpenseEstimatorBinding

    override fun getSelectedNavigationItemId(): Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseEstimatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUniversalNavigation(
            binding.rootView,
            binding.topBar,
            binding.includeBottomNav.universalBottomNav
        )

        // Retrieve dynamic user inputs passed from RouteBuilderActivity
        val origin = intent.getStringExtra(IntentNavigator.EXTRA_ORIGIN).orEmpty().trim()
        val destination = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION).orEmpty().trim()
        val distanceMiles = intent.getIntExtra(IntentNavigator.EXTRA_DISTANCE, 0)
        val tripDays = intent.getIntExtra("EXTRA_DAYS", 4).coerceAtLeast(1)
        val tripTravelers = intent.getIntExtra("EXTRA_TRAVELERS", 2).coerceAtLeast(1)
        val initialBudgetUsd = intent.getIntExtra(IntentNavigator.EXTRA_BUDGET, 0)

        binding.tvDestinationHeader.text = if (destination.isNotEmpty()) {
            destination.uppercase()
        } else {
            "TRIP BUDGET BREAKDOWN"
        }

        binding.rvCategories.layoutManager = LinearLayoutManager(this)

        // Display immediate preliminary figures while Gemini AI generates itemized allocations
        if (initialBudgetUsd > 0) {
            val perPersonInitial = initialBudgetUsd / tripTravelers
            binding.tvTotalExpense.text = AITravelEngine.formatDualCurrency(initialBudgetUsd)
            binding.tvPerPerson.text = buildPerPersonSummary(
                perPersonUsd = perPersonInitial,
                travelers = tripTravelers,
                days = tripDays,
                distanceMiles = distanceMiles
            )
        } else {
            binding.tvTotalExpense.text = "Calculating..."
            binding.tvPerPerson.text = "Analyzing route & local rates..."
        }

        // Fetch AI-generated dynamic budget breakdown
        lifecycleScope.launch {
            val effectiveDistance = if (distanceMiles > 0) {
                distanceMiles
            } else if (origin.isNotEmpty() && destination.isNotEmpty()) {
                AITravelEngine.estimateDistance(origin, destination)
            } else {
                0
            }

            val aiBudget = AITravelEngine.generateSmartAIBudget(
                origin = origin.ifEmpty { "Origin" },
                destination = destination.ifEmpty { "Destination" },
                distanceMiles = effectiveDistance,
                days = tripDays,
                travelers = tripTravelers
            )

            val totalUsd = aiBudget.totalBudget.coerceAtLeast(1)
            val perPersonUsd = totalUsd / tripTravelers

            binding.tvTotalExpense.text = AITravelEngine.formatDualCurrency(totalUsd)
            binding.tvPerPerson.text = buildPerPersonSummary(
                perPersonUsd = perPersonUsd,
                travelers = tripTravelers,
                days = tripDays,
                distanceMiles = effectiveDistance
            )
            binding.rvCategories.adapter = ExpenseCategoryAdapter(aiBudget.categories)
        }

        // Open Day-by-Day AI Itinerary passing dynamic destination and days
        binding.btnOpenTimelineIntent.setOnClickListener {
            val timelineIntent = Intent(this, ItineraryTimelineActivity::class.java).apply {
                putExtra(IntentNavigator.EXTRA_DESTINATION, destination)
                putExtra(IntentNavigator.EXTRA_ORIGIN, origin)
                putExtra("EXTRA_DAYS", tripDays)
                putExtra("EXTRA_TRAVELERS", tripTravelers)
            }
            startActivity(timelineIntent)
        }

        // Return to RouteBuilderActivity to modify route or parameters
        binding.btnRerouteIntent.setOnClickListener {
            finish()
        }

        binding.btnQuickProfile.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }
    }

    private fun buildPerPersonSummary(
        perPersonUsd: Int,
        travelers: Int,
        days: Int,
        distanceMiles: Int
    ): String {
        val travelerLabel = if (travelers == 1) "1 traveler" else "$travelers travelers"
        val dayLabel = if (days == 1) "1 day" else "$days days"
        val base = "${AITravelEngine.formatDualCurrency(perPersonUsd)} / person • $travelerLabel ($dayLabel"
        return if (distanceMiles > 0) "$base, $distanceMiles mi)" else "$base)"
    }

    // Embedded RecyclerView Adapter bound to res/layout/item_expense_category.xml
    inner class ExpenseCategoryAdapter(
        private val categories: List<ExpenseCategory>
    ) : RecyclerView.Adapter<ExpenseCategoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(R.id.tvCategoryIcon)
            val tvName: TextView = view.findViewById(R.id.tvCategoryName)
            val tvSubtitle: TextView = view.findViewById(R.id.tvCategorySubtitle)
            val tvCost: TextView = view.findViewById(R.id.tvCategoryCost)
            val tvPercentage: TextView = view.findViewById(R.id.tvCategoryPercentage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = categories[position]
            holder.tvIcon.text = item.icon
            holder.tvName.text = item.name
            holder.tvSubtitle.text = item.subtitle
            holder.tvCost.text = item.cost
            holder.tvPercentage.text = item.percentage
        }

        override fun getItemCount(): Int = categories.size
    }
}