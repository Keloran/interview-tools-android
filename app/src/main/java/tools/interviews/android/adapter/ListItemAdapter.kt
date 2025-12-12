package tools.interviews.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import tools.interviews.android.R
import tools.interviews.android.model.ListItem
import java.time.format.DateTimeFormatter

class ListItemAdapter : ListAdapter<ListItem, ListItemAdapter.ViewHolder>(ListItemDiffCallback()) {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textCompanyName: TextView = itemView.findViewById(R.id.textCompanyName)
        private val textDate: TextView = itemView.findViewById(R.id.textDate)
        private val chipLabel1: Chip = itemView.findViewById(R.id.chipLabel1)
        private val chipLabel2: Chip = itemView.findViewById(R.id.chipLabel2)

        fun bind(item: ListItem) {
            textName.text = item.name
            textCompanyName.text = item.companyName
            textDate.text = item.date.format(dateFormatter)
            chipLabel1.text = item.label1
            chipLabel2.text = item.label2
        }
    }

    private class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }
}
