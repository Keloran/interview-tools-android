package tools.interviews.android.calendar

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.ViewContainer
import tools.interviews.android.R
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MonthViewContainer(view: View) : ViewContainer(view) {
    private val monthYearText: TextView = view.findViewById(R.id.monthYearText)
    private val buttonPrevMonth: ImageButton = view.findViewById(R.id.buttonPrevMonth)
    private val buttonNextMonth: ImageButton = view.findViewById(R.id.buttonNextMonth)
    private val weekDaysContainer: LinearLayout = view.findViewById(R.id.weekDaysContainer)

    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun bind(data: CalendarMonth, calendarView: CalendarView) {
        monthYearText.text = data.yearMonth.format(monthYearFormatter)

        buttonPrevMonth.setOnClickListener {
            calendarView.findFirstVisibleMonth()?.let {
                calendarView.smoothScrollToMonth(it.yearMonth.minusMonths(1))
            }
        }

        buttonNextMonth.setOnClickListener {
            calendarView.findFirstVisibleMonth()?.let {
                calendarView.smoothScrollToMonth(it.yearMonth.plusMonths(1))
            }
        }

        // Setup weekday headers (only once)
        if (weekDaysContainer.childCount == 0) {
            val orderedDays = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
            )
            // Get theme-aware color
            val typedValue = android.util.TypedValue()
            view.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            val textColor = typedValue.data

            orderedDays.forEach { dayOfWeek ->
                val textView = TextView(view.context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                    setTextColor(textColor)
                }
                weekDaysContainer.addView(textView)
            }
        }
    }
}
