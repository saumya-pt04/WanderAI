package com.wanderai.travelplanner

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

abstract class BaseNavigationActivity : AppCompatActivity() {

    abstract fun getSelectedNavigationItemId(): Int

    protected fun setupUniversalNavigation(
        rootView: ViewGroup,
        topBar: View,
        bottomNavContainer: View
    ) {
        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()

        // 1. Unify topBar background, elevation, minHeight & padding
        topBar.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_mint))
        topBar.elevation = 4f * d
        topBar.minimumHeight = dp(58)

        val baseTopPad = dp(12)
        val baseBottomPad = dp(12)
        val baseHorizPad = dp(16)

        // 2. Automatically unify all icons, buttons, and title TextViews inside topBar recursively
        unifyHeaderChildren(topBar)

        // 3. Automatically wire up standard header actions if present
        topBar.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        topBar.findViewById<View>(R.id.btnQuickProfile)?.setOnClickListener {
            if (this !is TripHistoryProfileActivity) {
                IntentNavigator.navigateToTripHistory(this)
            }
        }

        // 4. Apply consistent edge-to-edge WindowInsets across all screens
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(
                baseHorizPad,
                systemBars.top + baseTopPad,
                baseHorizPad,
                baseBottomPad
            )
            bottomNavContainer.setPadding(
                bottomNavContainer.paddingLeft,
                bottomNavContainer.paddingTop,
                bottomNavContainer.paddingRight,
                systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)

        // 5. Wire universal BottomNavigationView across all pages
        val bottomNav = (bottomNavContainer as? BottomNavigationView)
            ?: bottomNavContainer.findViewById(R.id.universalBottomNav)

        bottomNav?.apply {
            val selectedId = getSelectedNavigationItemId()
            if (menu.findItem(selectedId) != null) {
                selectedItemId = selectedId
            }

            setOnItemSelectedListener { item ->
                if (item.itemId == getSelectedNavigationItemId()) {
                    return@setOnItemSelectedListener true
                }
                when (item.itemId) {
                    R.id.nav_route_builder -> {
                        IntentNavigator.navigateToRouteBuilder(this@BaseNavigationActivity)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    R.id.nav_camp_map -> {
                        IntentNavigator.navigateToCampMap(this@BaseNavigationActivity)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    R.id.nav_packing_list -> {
                        IntentNavigator.navigateToPackingList(this@BaseNavigationActivity)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    R.id.nav_group_split -> {
                        IntentNavigator.navigateToGroupSplit(this@BaseNavigationActivity)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    R.id.nav_trip_history -> {
                        IntentNavigator.navigateToTripHistory(this@BaseNavigationActivity)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun unifyHeaderChildren(view: View) {
        if (view !is ViewGroup) return

        val d = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()

        val roseTint = ContextCompat.getColor(this, R.color.surface_tint)
        val burgundy = ContextCompat.getColor(this, R.color.brand_mint)
        val gold = ContextCompat.getColor(this, R.color.brand_coral)

        if (view is LinearLayout) {
            view.gravity = Gravity.CENTER_VERTICAL
        }

        val childCount = view.childCount
        for (i in 0 until childCount) {
            val child = view.getChildAt(i)
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams

            when (child) {
                is MaterialButton -> {
                    lp?.width = dp(40)
                    lp?.height = dp(40)
                    if (i == 0 && lp != null) lp.marginEnd = dp(12)
                    child.layoutParams = lp
                    child.insetTop = 0
                    child.insetBottom = 0
                    child.setPadding(0, 0, 0, 0)
                    child.cornerRadius = dp(20)
                    child.iconSize = dp(18)
                    child.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    child.iconPadding = 0
                    child.backgroundTintList = ColorStateList.valueOf(roseTint)
                    child.iconTint = ColorStateList.valueOf(burgundy)
                }

                is ImageButton -> {
                    lp?.width = dp(40)
                    lp?.height = dp(40)
                    if (i == 0 && lp != null) lp.marginEnd = dp(12)
                    child.layoutParams = lp

                    val circleBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(roseTint)
                    }
                    child.background = circleBg
                    child.backgroundTintList = null
                    child.setPadding(dp(11), dp(11), dp(11), dp(11))
                    child.scaleType = ImageView.ScaleType.FIT_CENTER
                    child.imageTintList = ColorStateList.valueOf(burgundy)
                }

                is ImageView -> {
                    val entryName = if (child.id != View.NO_ID) {
                        try { resources.getResourceEntryName(child.id) } catch (_: Exception) { "" }
                    } else ""

                    val isBrandLogo = entryName.contains("logo", ignoreCase = true) || child.id == R.id.ivLogo

                    if (isBrandLogo) {
                        lp?.width = dp(28)
                        lp?.height = dp(28)
                        lp?.marginEnd = dp(10)
                        child.layoutParams = lp
                        child.imageTintList = ColorStateList.valueOf(gold)
                    } else {
                        lp?.width = dp(40)
                        lp?.height = dp(40)
                        if (i == 0 && lp != null) lp.marginEnd = dp(12)
                        child.layoutParams = lp

                        val circleBg = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(roseTint)
                        }
                        child.background = circleBg
                        child.backgroundTintList = null
                        child.setPadding(dp(11), dp(11), dp(11), dp(11))
                        child.scaleType = ImageView.ScaleType.FIT_CENTER
                        child.imageTintList = ColorStateList.valueOf(burgundy)
                    }
                }

                is TextView -> {
                    val textStr = child.text.toString().trim()
                    val entryName = if (child.id != View.NO_ID) {
                        try { resources.getResourceEntryName(child.id) } catch (_: Exception) { "" }
                    } else ""

                    val isRightAction = textStr.equals("Sign Out", ignoreCase = true) ||
                            entryName.contains("signOut", ignoreCase = true)

                    val isSubtitle = entryName.contains("context", ignoreCase = true) ||
                            entryName.contains("sub", ignoreCase = true)

                    if (isRightAction) {
                        child.setTextColor(gold)
                        child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        child.setTypeface(null, Typeface.BOLD)
                    } else if (isSubtitle) {
                        child.setTextColor(gold)
                        child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        child.setTypeface(null, Typeface.BOLD)
                    } else {
                        child.setTextColor(Color.WHITE)
                        child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        child.setTypeface(null, Typeface.BOLD)
                    }
                }

                is ViewGroup -> {
                    unifyHeaderChildren(child)
                }
            }
        }
    }
}
