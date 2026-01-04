package tools.interviews.android.calendar

import tools.interviews.android.model.InterviewOutcome
import java.time.LocalDate

/**
 * Represents pip indicators for a single date
 */
data class CalendarPipData(
    val date: LocalDate,
    val pips: List<InterviewOutcome>  // Max 5, already priority-sorted
)

/**
 * Container for all pip data keyed by date
 */
typealias PipDataMap = Map<LocalDate, CalendarPipData>
