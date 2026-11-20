package com.wanderai.travelplanner

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanderai.travelplanner.databinding.ActivityGroupSplitBinding

data class SplitMember(
    val name: String,
    var isPaid: Boolean = false
)

class GroupSplitActivity : BaseNavigationActivity() {

    private lateinit var binding: ActivityGroupSplitBinding
    private val membersList = mutableListOf<SplitMember>()
    private lateinit var adapter: SplitAdapter

    private var destinationName: String = ""
    private var totalTripUsd: Int = 0
    private var lastSyncedSignature: String = ""

    override fun getSelectedNavigationItemId(): Int = R.id.nav_group_split

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupSplitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUniversalNavigation(
            binding.rootView,
            binding.topBar,
            binding.includeBottomNav.universalBottomNav
        )

        adapter = SplitAdapter(
            members = membersList,
            onTogglePaid = { position ->
                if (position in membersList.indices) {
                    membersList[position].isPaid = !membersList[position].isPaid
                    adapter.notifyItemChanged(position)
                }
            },
            onRemoveMember = { position ->
                if (position in membersList.indices) {
                    membersList.removeAt(position)
                    recalculateSplitUI()
                }
            }
        )

        binding.rvSplitMembers.layoutManager = LinearLayoutManager(this)
        binding.rvSplitMembers.adapter = adapter

        binding.btnAddMember.setOnClickListener {
            val name = binding.etMemberName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter traveler name first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            membersList.add(SplitMember(name = name, isPaid = false))
            binding.etMemberName.text.clear()
            recalculateSplitUI()
        }

        binding.btnShareSplit.setOnClickListener {
            if (totalTripUsd <= 0 || membersList.isEmpty()) {
                Toast.makeText(this, "Select a destination and at least 1 traveler first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val perPersonUsd = totalTripUsd / membersList.size
            val summary = buildString {
                appendLine("🌍 WanderAI Trip Split: $destinationName")
                appendLine("Total Cost: ${AITravelEngine.formatDualCurrency(totalTripUsd)}")
                appendLine("Per Person (${membersList.size}): ${AITravelEngine.formatDualCurrency(perPersonUsd)}")
                appendLine()
                membersList.forEachIndexed { idx, m ->
                    val status = if (m.isPaid) "✅ Paid" else "⏳ Pending"
                    appendLine("${idx + 1}. ${m.name}: ${AITravelEngine.formatDualCurrency(perPersonUsd)} ($status)")
                }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, summary)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Trip Split"))
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnQuickProfile.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }
    }

    override fun onResume() {
        super.onResume()
        syncTripSplitState()
    }

    private fun syncTripSplitState() {
        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)
        val origin = prefs.getString("LAST_ORIGIN", "").orEmpty().trim()
        val destination = (intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION)
            ?: prefs.getString("LAST_DESTINATION", "")).orEmpty().trim()

        val days = (prefs.all["LAST_DAYS"] as? Int ?: 0).coerceAtLeast(0)
        val memberCount = (prefs.all["LAST_MEMBERS"] as? Int ?: 0).coerceAtLeast(0)
        val savedBudgetUsd = (prefs.all["LAST_TOTAL_BUDGET_USD"] as? Int ?: 0).coerceAtLeast(0)

        val currentSignature = "$origin|$destination|$days|$memberCount|$savedBudgetUsd"
        if (currentSignature == lastSyncedSignature) return
        lastSyncedSignature = currentSignature

        destinationName = destination

        // Strictly 0 cost and 0 members when no location or 0 days/members are selected
        if (origin.isEmpty() || destination.isEmpty() || days == 0 || memberCount == 0) {
            totalTripUsd = 0
            membersList.clear()
            binding.tvSplitDestination.text = "NO LOCATION SELECTED"
            recalculateSplitUI()
            return
        }

        totalTripUsd = if (savedBudgetUsd > 0) {
            savedBudgetUsd
        } else {
            val dist = AITravelEngine.estimateDistance(origin, destination)
            AITravelEngine.generateBudget(destination, dist, days, memberCount).totalBudget
        }

        binding.tvSplitDestination.text = "${destination.uppercase()} ($days DAYS)"

        membersList.clear()
        for (i in 1..memberCount) {
            membersList.add(SplitMember(name = "Traveler $i", isPaid = (i == 1)))
        }

        recalculateSplitUI()
    }

    private fun recalculateSplitUI() {
        val count = membersList.size
        val perPersonUsd = if (count > 0 && totalTripUsd > 0) totalTripUsd / count else 0

        binding.tvTotalSplitAmount.text = AITravelEngine.formatDualCurrency(totalTripUsd)
        binding.tvPerPersonShare.text = AITravelEngine.formatDualCurrency(perPersonUsd)
        binding.tvMembersHeader.text = "TRAVELERS & EQUAL SPLIT ($count)"
        adapter.notifyDataSetChanged()
    }

    inner class SplitAdapter(
        private val members: List<SplitMember>,
        private val onTogglePaid: (Int) -> Unit,
        private val onRemoveMember: (Int) -> Unit
    ) : RecyclerView.Adapter<SplitAdapter.SplitViewHolder>() {

        inner class SplitViewHolder(
            val card: LinearLayout,
            val tvName: TextView,
            val tvShare: TextView,
            val tvStatus: TextView,
            val btnRemove: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SplitViewHolder {
            val ctx = parent.context
            val d = ctx.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * d).toInt()

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_card_glass)
                elevation = dp(1).toFloat()
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            val infoCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.text_high_emphasis))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }

            val tvShare = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.brand_coral))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            }

            infoCol.addView(tvName)
            infoCol.addView(tvShare)

            val tvStatus = TextView(ctx).apply {
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_input_field)
            }

            val btnRemove = TextView(ctx).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_dim))
                setPadding(dp(12), dp(4), dp(4), dp(4))
            }

            card.addView(infoCol)
            card.addView(tvStatus)
            card.addView(btnRemove)

            return SplitViewHolder(card, tvName, tvShare, tvStatus, btnRemove)
        }

        override fun onBindViewHolder(holder: SplitViewHolder, position: Int) {
            val member = members[position]
            val perPersonUsd = if (members.isNotEmpty() && totalTripUsd > 0) {
                totalTripUsd / members.size
            } else {
                0
            }

            holder.tvName.text = member.name
            holder.tvShare.text = AITravelEngine.formatDualCurrency(perPersonUsd)

            if (member.isPaid) {
                holder.tvStatus.text = "PAID"
                holder.tvStatus.setTextColor(Color.parseColor("#1E7E6C"))
            } else {
                holder.tvStatus.text = "PENDING"
                holder.tvStatus.setTextColor(ContextCompat.getColor(holder.card.context, R.color.brand_coral))
            }

            holder.tvStatus.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onTogglePaid(pos)
            }
            holder.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemoveMember(pos)
            }
        }

        override fun getItemCount(): Int = members.size
    }
}