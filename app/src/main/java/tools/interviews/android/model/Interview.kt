package tools.interviews.android.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Interview(
    val id: Long,
    val jobTitle: String,
    val companyName: String,
    val clientCompany: String? = null,
    val stage: InterviewStage,
    val method: InterviewMethod? = null,
    val outcome: InterviewOutcome = InterviewOutcome.SCHEDULED,
    val applicationDate: LocalDate,
    val interviewDate: LocalDateTime? = null,
    val deadline: LocalDateTime? = null,
    val interviewer: String? = null,
    val link: String? = null,
    val jobListing: String? = null,
    val notes: String? = null
)
