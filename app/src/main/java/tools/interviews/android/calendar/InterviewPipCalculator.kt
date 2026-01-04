package tools.interviews.android.calendar

import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewOutcome
import java.time.LocalDate

object InterviewPipCalculator {

    private const val MAX_PIPS_PER_DAY = 5

    /**
     * Priority ordering for outcomes (lower = higher priority)
     * Passed/Rejected > Offer states > Awaiting > Scheduled
     */
    private val outcomePriority = mapOf(
        InterviewOutcome.PASSED to 1,
        InterviewOutcome.REJECTED to 1,
        InterviewOutcome.OFFER_RECEIVED to 2,
        InterviewOutcome.OFFER_ACCEPTED to 2,
        InterviewOutcome.OFFER_DECLINED to 3,
        InterviewOutcome.WITHDREW to 3,
        InterviewOutcome.AWAITING_RESPONSE to 4,
        InterviewOutcome.SCHEDULED to 5
    )

    /**
     * Computes pip data for all dates from a list of interviews.
     * Each interview appears on ALL its relevant dates (applicationDate, deadline, interviewDate).
     */
    fun computePipData(interviews: List<Interview>): PipDataMap {
        val dateToOutcomes = mutableMapOf<LocalDate, MutableList<InterviewOutcome>>()

        interviews.forEach { interview ->
            val outcome = interview.outcome
            val dates = mutableListOf<LocalDate>()

            // Add all relevant dates for this interview
            dates.add(interview.applicationDate)

            interview.deadline?.toLocalDate()?.let { dates.add(it) }
            interview.interviewDate?.toLocalDate()?.let { dates.add(it) }

            // Add outcome to each date (avoiding duplicates for same date)
            dates.distinct().forEach { date ->
                dateToOutcomes.getOrPut(date) { mutableListOf() }.add(outcome)
            }
        }

        return dateToOutcomes.mapValues { (date, outcomes) ->
            val sortedOutcomes = outcomes
                .sortedBy { outcomePriority[it] ?: Int.MAX_VALUE }
                .take(MAX_PIPS_PER_DAY)

            CalendarPipData(date, sortedOutcomes)
        }
    }
}
