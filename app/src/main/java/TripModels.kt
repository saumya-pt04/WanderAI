package com.wanderai.travelplanner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanderai.travelplanner.databinding.ItemExpenseCategoryBinding
import com.wanderai.travelplanner.databinding.ItemGroupSplitExpenseBinding
import com.wanderai.travelplanner.databinding.ItemPackingChecklistBinding
import com.wanderai.travelplanner.databinding.ItemTimelineStopBinding

// --- Models ---
data class ExpenseCategory(
    val name: String,
    val subtitle: String,
    val cost: String,
    val percentage: String,
    val icon: String
)

data class TimelineStop(
    val time: String,
    val title: String,
    val subtitle: String = "",
    val badge: String = "",
    val cost: String = "",
    val description: String = subtitle
)

data class PackingItem(
    val name: String,
    val subtitle: String,
    val category: String,
    val icon: String = "",
    var isPacked: Boolean = false
) {
    val tag: String get() = category
}

data class GroupExpense(
    val title: String,
    val paidBy: String,
    val amountDual: String,
    val icon: String
)

data class TripBudgetResult(
    val totalBudget: Int,
    val categories: List<ExpenseCategory>
)

data class WeatherSegment(
    val timeSlot: String,
    val condition: String,
    val temperature: String,
    val icon: String
)

data class TripWeatherResult(
    val headline: String,
    val advisory: String,
    val isWarning: Boolean,
    val segments: List<WeatherSegment>
)

// --- Adapters ---

class ExpenseCategoryAdapter(private val items: List<ExpenseCategory>) :
    RecyclerView.Adapter<ExpenseCategoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemExpenseCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvCategoryName.text = item.name
        holder.binding.tvCategorySubtitle.text = item.subtitle
        holder.binding.tvCategoryCost.text = item.cost
        holder.binding.tvCategoryPercentage.text = item.percentage
        holder.binding.tvCategoryIcon.text = item.icon
    }

    override fun getItemCount() = items.size
}

class TimelineAdapter(private val items: List<TimelineStop>) :
    RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTimelineStopBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimelineStopBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTime.text = item.time
        holder.binding.tvCost.text = item.cost
        holder.binding.tvStopTitle.text = item.title
        holder.binding.tvStopDescription.text = item.description
    }

    override fun getItemCount() = items.size
}

class PackingListAdapter(
    private val items: MutableList<PackingItem>,
    private val onCheckChanged: (Int, Int) -> Unit
) : RecyclerView.Adapter<PackingListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPackingChecklistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPackingChecklistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvItemTitle.text = item.name
        holder.binding.tvItemSubtitle.text = item.subtitle
        holder.binding.tvItemCategory.text = item.tag
        holder.binding.tvItemEmoji.text = item.icon

        holder.binding.cbItemPacked.setOnCheckedChangeListener(null)
        holder.binding.cbItemPacked.isChecked = item.isPacked

        holder.binding.cbItemPacked.setOnCheckedChangeListener { _, isChecked ->
            item.isPacked = isChecked
            val packedCount = items.count { it.isPacked }
            onCheckChanged(packedCount, items.size)
        }
    }

    override fun getItemCount() = items.size
}

class GroupSplitAdapter(private val items: List<GroupExpense>) :
    RecyclerView.Adapter<GroupSplitAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemGroupSplitExpenseBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupSplitExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvBillTitle.text = item.title
        holder.binding.tvPaidBy.text = item.paidBy
        holder.binding.tvBillAmount.text = item.amountDual
        holder.binding.tvPayerAvatar.text = item.icon
    }

    override fun getItemCount() = items.size
}