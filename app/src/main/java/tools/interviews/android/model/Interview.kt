package tools.interviews.android.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "interviews")
data class Interview(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val notes: String? = null,
    val metadataJSON: String? = null
)
