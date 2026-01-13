package tools.interviews.android.calendar

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kizitonwose.calendar.view.ViewContainer
// import tools.interviews.android.R
import android_app.R

class DayViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.calendarDayText)
    val pipContainer: LinearLayout = view.findViewById(R.id.pipContainer)
}
