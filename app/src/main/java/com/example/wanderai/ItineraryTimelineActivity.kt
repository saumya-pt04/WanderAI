package com.wanderai.travelplanner

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanderai.travelplanner.databinding.ActivityItineraryTimelineBinding
import kotlinx.coroutines.launch

class ItineraryTimelineActivity : BaseNavigationActivity() {

    private lateinit var binding: ActivityItineraryTimelineBinding

    override fun getSelectedNavigationItemId(): Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityItineraryTimelineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUniversalNavigation(
            binding.rootView,
            binding.topBar,
            binding.includeBottomNav.universalBottomNav
        )

        val destination = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION) ?: "Los Angeles, CA"
        val tripDays = intent.getIntExtra("EXTRA_DAYS", 4)

        binding.tvTimelineTitle.text = "$destination ($tripDays Days)"
        binding.tvDaySubheader.text = "AI Curating Your Itinerary..."
        binding.rvTimeline.layoutManager = LinearLayoutManager(this)

        // Fetch AI-generated itinerary stops from AITravelEngine
        lifecycleScope.launch {
            val aiStops = AITravelEngine.generateAIItinerary(destination, tripDays)
            binding.tvDaySubheader.text = "Curated Expedition Stops"
            val timelineStops = aiStops.map { TimelineStop(time = it.first, title = it.second, description = it.third) }
            binding.rvTimeline.adapter = TimelineStopAdapter(timelineStops)
        }

        binding.btnQuickWeather.setOnClickListener {
            IntentNavigator.navigateToWeatherMicroclimate(this, destination = destination)
        }

        binding.btnQuickProfile.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }
    }

    // Self-contained Two-Color Styled RecyclerView Adapter
    inner class TimelineStopAdapter(
        private val stops: List<TimelineStop>
    ) : RecyclerView.Adapter<TimelineStopAdapter.StopViewHolder>() {

        inner class StopViewHolder(
            val container: LinearLayout,
            val tvTime: TextView,
            val tvTitle: TextView,
            val tvDesc: TextView
        ) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopViewHolder {
            val context = parent.context
            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_glass)
                elevation = dp(2).toFloat()
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            }

            // Sunset Coral Time Badge (#FF5B37)
            val tvTime = TextView(context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.brand_coral))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
            }

            // High-Emphasis Slate Title
            val tvTitle = TextView(context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_high_emphasis))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(2))
            }

            // Medium-Emphasis Description
            val tvDesc = TextView(context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_medium_emphasis))
                textSize = 13f
            }

            card.addView(tvTime)
            card.addView(tvTitle)
            card.addView(tvDesc)

            return StopViewHolder(card, tvTime, tvTitle, tvDesc)
        }

        override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
            val stop = stops[position]
            holder.tvTime.text = "🕒 ${stop.time}"
            holder.tvTitle.text = stop.title
            holder.tvDesc.text = stop.description
        }

        override fun getItemCount(): Int = stops.size
    }
}