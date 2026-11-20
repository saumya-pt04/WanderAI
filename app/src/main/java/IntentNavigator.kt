package com.wanderai.travelplanner

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

object IntentNavigator {

    const val EXTRA_ORIGIN = "EXTRA_ORIGIN"
    const val EXTRA_DESTINATION = "EXTRA_DESTINATION"
    const val EXTRA_BUDGET = "EXTRA_BUDGET"
    const val EXTRA_DISTANCE = "EXTRA_DISTANCE"

    fun navigateToLogin(context: Context) {
        val intent = Intent(context, LoginActivity::class.java)
        context.startActivity(intent)
    }

    fun navigateToRouteBuilder(
        context: Context,
        origin: String = "",
        destination: String = ""
    ) {
        val intent = Intent(context, RouteBuilderActivity::class.java).apply {
            if (origin.isNotEmpty()) putExtra(EXTRA_ORIGIN, origin)
            if (destination.isNotEmpty()) putExtra(EXTRA_DESTINATION, destination)
        }
        context.startActivity(intent)
    }

    fun navigateToCampMap(
        context: Context,
        origin: String = "",
        destination: String = ""
    ) {
        val intent = Intent(context, CampMapActivity::class.java).apply {
            if (origin.isNotEmpty()) putExtra(EXTRA_ORIGIN, origin)
            if (destination.isNotEmpty()) putExtra(EXTRA_DESTINATION, destination)
        }
        context.startActivity(intent)
    }

    fun navigateToPackingList(context: Context, destination: String = "") {
        val intent = Intent(context, PackingListActivity::class.java).apply {
            if (destination.isNotEmpty()) putExtra(EXTRA_DESTINATION, destination)
        }
        context.startActivity(intent)
    }

    fun navigateToGroupSplit(context: Context, destination: String = "") {
        val intent = Intent(context, GroupSplitActivity::class.java).apply {
            if (destination.isNotEmpty()) putExtra(EXTRA_DESTINATION, destination)
        }
        context.startActivity(intent)
    }

    fun navigateToWeatherMicroclimate(context: Context, destination: String = "") {
        val intent = Intent(context, WeatherMicroclimateActivity::class.java).apply {
            if (destination.isNotEmpty()) putExtra(EXTRA_DESTINATION, destination)
        }
        context.startActivity(intent)
    }

    fun navigateToExpenseEstimator(
        context: Context,
        origin: String = "",
        destination: String = "",
        budget: Int = 0,
        distance: Int = 0,
        days: Int = 1,
        travelers: Int = 1
    ) {
        val intent = Intent(context, ExpenseEstimatorActivity::class.java).apply {
            if (origin.isNotEmpty()) putExtra(EXTRA_ORIGIN, origin)
            if (destination.isNotEmpty()) putExtra(EXTRA_DESTINATION, destination)
            if (budget > 0) putExtra(EXTRA_BUDGET, budget)
            if (distance > 0) putExtra(EXTRA_DISTANCE, distance)
            putExtra("EXTRA_DAYS", days)
            putExtra("EXTRA_TRAVELERS", travelers)
        }
        context.startActivity(intent)
    }

    // Triggered when user clicks the Profile icon (top bar or bottom nav)
    fun navigateToTripHistory(context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val loginIntent = Intent(context, LoginActivity::class.java).apply {
                putExtra("REDIRECT_TO_PROFILE", true)
            }
            context.startActivity(loginIntent)
        } else {
            val profileIntent = Intent(context, TripHistoryProfileActivity::class.java)
            context.startActivity(profileIntent)
        }
    }
}