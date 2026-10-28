package tools.interviews.android.model

import tools.interviews.android.data.account.AccountOwners
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime

@Entity(
    tableName = "interviews",
    foreignKeys = [
        ForeignKey(
            entity = Company::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["companyId"]), Index(value = ["ownerId"])]
)
data class Interview(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "'guest'") val ownerId: String = AccountOwners.GUEST,
    val serverId: Int? = null, // Server ID - null means not synced yet
    val companyId: Long? = null, // Foreign key to Company table
    val jobTitle: String,
    val companyName: String, // Kept for backward compatibility and display
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
    val metadataJSON: String? = null,
    // Legacy wall-clock columns remain recoverable, with no assumed timezone.
    val interviewInstant: Instant? = null,
    val deadlineInstant: Instant? = null,
    val offerRespondBy: LocalDate? = null
)
