package com.wanderai.travelplanner

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.wanderai.travelplanner.databinding.ActivityTripHistoryProfileBinding

data class ExplorerTripItem(
    val title: String,
    val origin: String,
    val destination: String,
    val days: Int,
    val budgetUsd: Int,
    val distanceMiles: Int
)

class TripHistoryProfileActivity : BaseNavigationActivity() {

    private lateinit var binding: ActivityTripHistoryProfileBinding

    override fun getSelectedNavigationItemId(): Int = R.id.nav_trip_history

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripHistoryProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUniversalNavigation(
            binding.rootView,
            binding.topBar,
            binding.includeBottomNav.universalBottomNav
        )

        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)
        val user = FirebaseAuth.getInstance().currentUser
        val savedUsername = prefs.getString("USER_NAME", "")?.trim().orEmpty()
        val displayName = savedUsername.ifEmpty {
            user?.displayName?.takeIf { it.isNotBlank() } ?: "Saumya Patel"
        }

        binding.tvExplorerName.text = displayName

        val trips = mutableListOf<ExplorerTripItem>()

        // Load latest planned trip if present
        val lastDest = prefs.getString("LAST_DESTINATION", "").orEmpty()
        val lastOrigin = prefs.getString("LAST_ORIGIN", "").orEmpty()
        val lastDays = prefs.getInt("LAST_DAYS", 0)
        val lastBudget = prefs.getInt("LAST_TOTAL_BUDGET_USD", 0)
        if (lastDest.isNotEmpty() && lastDays > 0 && lastBudget > 0) {
            trips.add(
                ExplorerTripItem(
                    title = lastDest,
                    origin = lastOrigin.ifEmpty { "Starting City" },
                    destination = lastDest,
                    days = lastDays,
                    budgetUsd = lastBudget,
                    distanceMiles = AITravelEngine.estimateDistance(lastOrigin, lastDest)
                )
            )
        }

        // Default featured expedition
        trips.add(
            ExplorerTripItem(
                title = "Pacific Coast Highway",
                origin = "San Francisco, CA",
                destination = "Los Angeles, CA",
                days = 4,
                budgetUsd = 1755,
                distanceMiles = 450
            )
        )

        binding.tvExplorerSubtitle.text = "Travel Explorer • ${trips.size + 13} Journeys"

        binding.rvSavedTrips.apply {
            layoutManager = LinearLayoutManager(this@TripHistoryProfileActivity)
            adapter = SavedTripsAdapter(trips) { trip ->
                val intent = Intent(this@TripHistoryProfileActivity, ExpenseEstimatorActivity::class.java).apply {
                    putExtra(IntentNavigator.EXTRA_ORIGIN, trip.origin)
                    putExtra(IntentNavigator.EXTRA_DESTINATION, trip.destination)
                    putExtra(IntentNavigator.EXTRA_BUDGET, trip.budgetUsd)
                    putExtra(IntentNavigator.EXTRA_DISTANCE, trip.distanceMiles)
                    putExtra("EXTRA_DAYS", trip.days)
                    putExtra("EXTRA_TRAVELERS", 2)
                }
                startActivity(intent)
            }
        }

        binding.btnPlanNewTrip.setOnClickListener {
            startActivity(Intent(this, RouteBuilderActivity::class.java))
        }

        binding.btnSignOut.setOnClickListener {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (_: Exception) {
            }
            prefs.edit().remove("USER_NAME").apply()
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    inner class SavedTripsAdapter(
        private val items: List<ExplorerTripItem>,
        private val onClick: (ExplorerTripItem) -> Unit
    ) : RecyclerView.Adapter<SavedTripsAdapter.ViewHolder>() {

        inner class ViewHolder(
            val container: LinearLayout,
            val tvTitle: TextView,
            val tvSubtitle: TextView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * d).toInt()

            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_card_glass)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            }

            val tvTitle = TextView(ctx).apply {
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_high_emphasis))
            }

            val tvSubtitle = TextView(ctx).apply {
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.brand_coral))
                setPadding(0, dp(4), 0, 0)
            }

            container.addView(tvTitle)
            container.addView(tvSubtitle)
            return ViewHolder(container, tvTitle, tvSubtitle)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trip = items[position]
            val formattedBudget = AITravelEngine.formatDualCurrency(trip.budgetUsd)
            holder.tvTitle.text = trip.title
            holder.tvSubtitle.text = "${trip.days} Days • $formattedBudget • Tap to view budget"
            holder.container.setOnClickListener { onClick(trip) }
        }

        override fun getItemCount(): Int = items.size
    }
}