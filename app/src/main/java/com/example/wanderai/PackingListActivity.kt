package com.wanderai.travelplanner

import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

data class PackingGearItem(
    val title: String,
    val subtitle: String,
    val category: String,
    val iconEmoji: String,
    var isPacked: Boolean = false
)

class PackingListActivity : BaseNavigationActivity() {

    private lateinit var tvDestinationLabel: TextView
    private lateinit var tvPackedProgress: TextView
    private lateinit var progressBarPacking: android.widget.ProgressBar
    private lateinit var etCustomGear: EditText
    private lateinit var btnAddGear: View
    private lateinit var rvPackingItems: RecyclerView

    private val gearItems = mutableListOf<PackingGearItem>()
    private lateinit var adapter: PackingAdapter

    override fun getSelectedNavigationItemId(): Int = R.id.nav_packing_list

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_packing_list)

        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val topBar = findViewById<View>(R.id.topBar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.universalBottomNav)
            ?: findViewById<BottomNavigationView>(R.id.includeBottomNav)
        if (topBar != null && bottomNav != null) {
            setupUniversalNavigation(rootView, topBar, bottomNav)
        }

        // Enforce unified Rose Tint circle & Deep Burgundy arrow on Back button
        findViewById<View>(R.id.btnBack)?.let { backBtn ->
            backBtn.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.surface_tint)
            )
            when (backBtn) {
                is MaterialButton -> backBtn.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_mint)
                )
                is ImageView -> backBtn.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_mint)
                )
            }
            backBtn.setOnClickListener { finish() }
        }

        findViewById<View>(R.id.btnQuickProfile)?.setOnClickListener {
            IntentNavigator.navigateToTripHistory(this)
        }

        tvDestinationLabel = findViewById(R.id.tvExpeditionDestination)
        tvPackedProgress = findViewById(R.id.tvPackedProgress)
        progressBarPacking = findViewById(R.id.progressBarPacking)
        etCustomGear = findViewById(R.id.etCustomGear)
        btnAddGear = findViewById(R.id.btnAddGear)
        rvPackingItems = findViewById(R.id.rvPackingItems)

        val prefs = getSharedPreferences("WanderAI_Prefs", MODE_PRIVATE)
        val destination = intent.getStringExtra(IntentNavigator.EXTRA_DESTINATION)
            ?.takeIf { it.isNotBlank() }
            ?: prefs.getString("LAST_DESTINATION", "Los Angeles, CA")
            ?: "Los Angeles, CA"

        tvDestinationLabel.text = "EXPEDITION: ${destination.uppercase()}"

        gearItems.addAll(
            listOf(
                PackingGearItem(
                    title = "All-Weather Layer Jacket",
                    subtitle = "Essential for temperature shifts in $destination",
                    category = "Clothing",
                    iconEmoji = "🧥"
                ),
                PackingGearItem(
                    title = "20,000mAh Power Bank",
                    subtitle = "Keep navigation and camera charged on the road",
                    category = "Tech",
                    iconEmoji = "🔋"
                ),
                PackingGearItem(
                    title = "Reusable Insulated Flask",
                    subtitle = "Stay hydrated throughout transit",
                    category = "Essentials",
                    iconEmoji = "🎒"
                )
            )
        )

        adapter = PackingAdapter(gearItems) {
            updateProgressHeader()
        }

        rvPackingItems.apply {
            layoutManager = LinearLayoutManager(this@PackingListActivity)
            adapter = this@PackingListActivity.adapter
        }

        updateProgressHeader()

        btnAddGear.setOnClickListener {
            val customTitle = etCustomGear.text.toString().trim()
            if (customTitle.isEmpty()) {
                Toast.makeText(this, "Enter an item name first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            gearItems.add(
                0,
                PackingGearItem(
                    title = customTitle,
                    subtitle = "Custom expedition gear for $destination",
                    category = "Custom",
                    iconEmoji = "🧳"
                )
            )
            etCustomGear.setText("")
            adapter.notifyItemInserted(0)
            rvPackingItems.scrollToPosition(0)
            updateProgressHeader()
        }
    }

    private fun updateProgressHeader() {
        val total = gearItems.size
        val packed = gearItems.count { it.isPacked }
        val pct = if (total > 0) (packed * 100) / total else 0
        tvPackedProgress.text = "Packed $packed of $total Essentials ($pct%)"
        progressBarPacking.progress = pct
    }

    inner class PackingAdapter(
        private val items: List<PackingGearItem>,
        private val onStatusChanged: () -> Unit
    ) : RecyclerView.Adapter<PackingAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbPacked: CheckBox = view.findViewById(R.id.cbItemPacked)
            val tvIcon: TextView = view.findViewById(R.id.tvItemEmoji)
            val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
            val tvSubtitle: TextView = view.findViewById(R.id.tvItemSubtitle)
            val tvCategory: TextView = view.findViewById(R.id.tvItemCategory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_packing_checklist, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvIcon.text = item.iconEmoji
            holder.tvTitle.text = item.title
            holder.tvSubtitle.text = item.subtitle
            holder.tvCategory.text = item.category

            holder.cbPacked.setOnCheckedChangeListener(null)
            holder.cbPacked.isChecked = item.isPacked

            holder.tvTitle.paintFlags = if (item.isPacked) {
                holder.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            holder.cbPacked.setOnCheckedChangeListener { _, isChecked ->
                item.isPacked = isChecked
                notifyItemChanged(holder.bindingAdapterPosition)
                onStatusChanged()
            }

            holder.itemView.setOnClickListener {
                holder.cbPacked.isChecked = !holder.cbPacked.isChecked
            }
        }

        override fun getItemCount(): Int = items.size
    }
}