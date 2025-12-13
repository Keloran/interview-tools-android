package tools.interviews.android.adapter

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import tools.interviews.android.R
import tools.interviews.android.model.Interview
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class InterviewAdapter(
    private val onItemClick: (Interview) -> Unit = {},
    private val onAwaitingClick: (Interview) -> Unit = {},
    private val onNextStageClick: (Interview) -> Unit = {},
    private val onRejectClick: (Interview) -> Unit = {}
) : ListAdapter<Interview, InterviewAdapter.ViewHolder>(InterviewDiffCallback()) {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    // Track currently open item to close it when another is swiped
    private var currentlyOpenViewHolder: ViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_interview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun closeAllItems() {
        currentlyOpenViewHolder?.closeSwipe()
        currentlyOpenViewHolder = null
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContent: MaterialCardView = itemView.findViewById(R.id.cardContent)
        private val leftActions: View = itemView.findViewById(R.id.leftActions)
        private val rightActions: View = itemView.findViewById(R.id.rightActions)
        private val buttonAwaiting: View = itemView.findViewById(R.id.buttonAwaiting)
        private val buttonNextStage: View = itemView.findViewById(R.id.buttonNextStage)
        private val buttonReject: View = itemView.findViewById(R.id.buttonReject)
        private val outcomeIndicator: View = itemView.findViewById(R.id.outcomeIndicator)
        private val textJobTitle: TextView = itemView.findViewById(R.id.textJobTitle)
        private val textCompanyName: TextView = itemView.findViewById(R.id.textCompanyName)
        private val textInterviewDate: TextView = itemView.findViewById(R.id.textInterviewDate)
        private val badgeStage: TextView = itemView.findViewById(R.id.badgeStage)
        private val badgeMethod: TextView = itemView.findViewById(R.id.badgeMethod)

        private var startX = 0f
        private var startTranslationX = 0f
        private var isDragging = false
        private val leftActionsWidth = 144 * itemView.resources.displayMetrics.density // 72dp * 2
        private val rightActionsWidth = 72 * itemView.resources.displayMetrics.density
        private val fullSwipeThreshold = itemView.resources.displayMetrics.widthPixels * 0.6f

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

            // Interview date or deadline
            textInterviewDate.text = when {
                interview.interviewDate != null -> interview.interviewDate.format(dateTimeFormatter)
                interview.deadline != null -> "Deadline: ${interview.deadline.toLocalDate().format(dateFormatter)}"
                else -> "Applied ${interview.applicationDate.format(dateFormatter)}"
            }

            // Stage badge
            badgeStage.text = interview.stage.displayName

            // Method badge (only show if method is set)
            badgeMethod.isVisible = interview.method != null
            interview.method?.let {
                badgeMethod.text = it.displayName
            }

            // Reset card position
            cardContent.translationX = 0f

            // Setup touch handling for swipe
            setupTouchHandling(interview)

            // Action button clicks
            buttonAwaiting.setOnClickListener {
                onAwaitingClick(interview)
                closeSwipe()
            }

            buttonNextStage.setOnClickListener {
                onNextStageClick(interview)
                closeSwipe()
            }

            buttonReject.setOnClickListener {
                onRejectClick(interview)
                closeSwipe()
            }

            // Card click
            cardContent.setOnClickListener {
                if (cardContent.translationX == 0f) {
                    onItemClick(interview)
                } else {
                    closeSwipe()
                }
            }
        }

        private fun setupTouchHandling(interview: Interview) {
            cardContent.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startTranslationX = cardContent.translationX
                        isDragging = false
                        // Close any other open item
                        if (currentlyOpenViewHolder != this && currentlyOpenViewHolder != null) {
                            currentlyOpenViewHolder?.closeSwipe()
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - startX
                        if (abs(deltaX) > 10) {
                            isDragging = true
                            view.parent.requestDisallowInterceptTouchEvent(true)

                            var newTranslation = startTranslationX + deltaX
                            val maxSwipe = itemView.width.toFloat()

                            // Allow full swipe but with resistance after buttons are revealed
                            if (newTranslation > leftActionsWidth) {
                                val extra = newTranslation - leftActionsWidth
                                newTranslation = leftActionsWidth + extra * 0.3f
                            } else if (newTranslation < -rightActionsWidth) {
                                val extra = -rightActionsWidth - newTranslation
                                newTranslation = -rightActionsWidth - extra * 0.3f
                            }

                            // Limit to screen width
                            newTranslation = newTranslation.coerceIn(-maxSwipe * 0.8f, maxSwipe * 0.8f)

                            cardContent.translationX = newTranslation
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.parent.requestDisallowInterceptTouchEvent(false)
                        if (isDragging) {
                            val translation = cardContent.translationX
                            when {
                                // Full swipe right - trigger Awaiting Response
                                translation > fullSwipeThreshold -> {
                                    animateOffScreen(true) {
                                        onAwaitingClick(interview)
                                    }
                                }
                                // Full swipe left - trigger Rejected
                                translation < -fullSwipeThreshold -> {
                                    animateOffScreen(false) {
                                        onRejectClick(interview)
                                    }
                                }
                                // Partial swipe right - reveal buttons
                                translation > leftActionsWidth / 3 -> {
                                    animateToPosition(leftActionsWidth)
                                    currentlyOpenViewHolder = this
                                }
                                // Partial swipe left - reveal button
                                translation < -rightActionsWidth / 3 -> {
                                    animateToPosition(-rightActionsWidth)
                                    currentlyOpenViewHolder = this
                                }
                                // Small swipe - close
                                else -> {
                                    closeSwipe()
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }

        private fun animateOffScreen(toRight: Boolean, onComplete: () -> Unit) {
            val targetX = if (toRight) itemView.width.toFloat() else -itemView.width.toFloat()
            ValueAnimator.ofFloat(cardContent.translationX, targetX).apply {
                duration = 200
                addUpdateListener { animator ->
                    cardContent.translationX = animator.animatedValue as Float
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onComplete()
                        // Reset position after action
                        cardContent.translationX = 0f
                    }
                })
                start()
            }
        }

        private fun animateToPosition(targetX: Float) {
            ValueAnimator.ofFloat(cardContent.translationX, targetX).apply {
                duration = 150
                addUpdateListener { animator ->
                    cardContent.translationX = animator.animatedValue as Float
                }
                start()
            }
        }

        fun closeSwipe() {
            animateToPosition(0f)
            if (currentlyOpenViewHolder == this) {
                currentlyOpenViewHolder = null
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
