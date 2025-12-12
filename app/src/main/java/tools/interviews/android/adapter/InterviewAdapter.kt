package tools.interviews.android.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tools.interviews.android.R
import tools.interviews.android.model.Interview
import java.time.format.DateTimeFormatter

class InterviewAdapter(
    private val onItemClick: (Interview) -> Unit = {}
) : ListAdapter<Interview, InterviewAdapter.ViewHolder>(InterviewDiffCallback()) {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_interview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val outcomeIndicator: View = itemView.findViewById(R.id.outcomeIndicator)
        private val textJobTitle: TextView = itemView.findViewById(R.id.textJobTitle)
        private val textCompanyName: TextView = itemView.findViewById(R.id.textCompanyName)
        private val textInterviewDate: TextView = itemView.findViewById(R.id.textInterviewDate)
        private val badgeStage: TextView = itemView.findViewById(R.id.badgeStage)
        private val badgeMethod: TextView = itemView.findViewById(R.id.badgeMethod)

        fun bind(interview: Interview) {
            // Set outcome indicator color
            val indicatorDrawable = outcomeIndicator.background as? GradientDrawable
            indicatorDrawable?.setColor(
                ContextCompat.getColor(itemView.context, interview.outcome.colorRes)
            )

            // Job title
            textJobTitle.text = interview.jobTitle

            // Company name with optional client company
            val companyText = if (interview.clientCompany != null) {
                "${interview.companyName} (via ${interview.clientCompany})"
            } else {
                interview.companyName
            }
            textCompanyName.text = companyText

            // Interview date
            textInterviewDate.text = interview.interviewDate?.format(dateTimeFormatter)
                ?: "Applied ${interview.applicationDate.format(dateFormatter)}"

            // Stage badge
            badgeStage.text = interview.stage.displayName

            // Method badge (only show if method is set)
            badgeMethod.isVisible = interview.method != null
            interview.method?.let {
                badgeMethod.text = it.displayName
            }

            // Click listener
            itemView.setOnClickListener {
                onItemClick(interview)
            }
        }
    }

    private class InterviewDiffCallback : DiffUtil.ItemCallback<Interview>() {
        override fun areItemsTheSame(oldItem: Interview, newItem: Interview): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Interview, newItem: Interview): Boolean {
            return oldItem == newItem
        }
    }
}
