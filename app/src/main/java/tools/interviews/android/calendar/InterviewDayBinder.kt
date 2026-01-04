package tools.interviews.android.calendar

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import tools.interviews.android.R
import tools.interviews.android.model.InterviewOutcome
import java.time.LocalDate

class InterviewDayBinder(
    private val context: Context,
    private val onDateClick: (LocalDate) -> Unit
) : MonthDayBinder<DayViewContainer> {

    var pipData: PipDataMap = emptyMap()

    var selectedDate: LocalDate? = null

    override fun create(view: View): DayViewContainer = DayViewContainer(view)

    override fun bind(container: DayViewContainer, data: CalendarDay) {
        val day = data.date
        container.textView.text = day.dayOfMonth.toString()

        // Handle click
        container.view.setOnClickListener {
            if (data.position == DayPosition.MonthDate) {
                onDateClick(day)
            }
        }

        // Style based on position (current month vs adjacent months)
        when (data.position) {
            DayPosition.MonthDate -> {
                container.textView.alpha = 1f
                container.view.isClickable = true
            }
            DayPosition.InDate, DayPosition.OutDate -> {
                container.textView.alpha = 0.3f
                container.view.isClickable = false
            }
        }

        // Highlight selected date or today
        val isToday = day == LocalDate.now()
        val isSelected = day == selectedDate

        when {
            isSelected -> {
                // Selected date: primary background with onPrimary text
                container.textView.setBackgroundResource(R.drawable.selected_day_background)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                container.textView.setTextColor(typedValue.data)
            }
            isToday -> {
                // Today (not selected): primaryContainer background with onPrimaryContainer text
                container.textView.setBackgroundResource(R.drawable.today_day_background)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
                container.textView.setTextColor(typedValue.data)
            }
            else -> {
                container.textView.background = null
                // Use theme-aware colors
                val typedValue = android.util.TypedValue()
                if (data.position == DayPosition.MonthDate) {
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                } else {
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                }
                container.textView.setTextColor(typedValue.data)
            }
        }

        // Render pips
        renderPips(container.pipContainer, pipData[day])
    }

    private fun renderPips(container: LinearLayout, pipData: CalendarPipData?) {
        container.removeAllViews()

        pipData?.pips?.forEach { outcome ->
            val pip = View(context).apply {
                val size = (6 * context.resources.displayMetrics.density).toInt()
                val margin = (1 * context.resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, outcome.colorRes))
                }
            }
            container.addView(pip)
        }
    }
}
