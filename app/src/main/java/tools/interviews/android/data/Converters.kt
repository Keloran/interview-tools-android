package tools.interviews.android.data

import androidx.room.TypeConverter
import tools.interviews.android.model.InterviewMethod
import tools.interviews.android.model.InterviewOutcome
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromInstant(value: Instant?): String? = value?.toString()
    @TypeConverter fun toInstant(value: String?): Instant? = value?.let(Instant::parse)

    // LocalDate converters
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    // LocalDateTime converters
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it) }
    }

    // InterviewStage converters
    @TypeConverter
    fun fromInterviewStage(value: InterviewStage): String {
        return value.name
    }

    @TypeConverter
    fun toInterviewStage(value: String): InterviewStage {
        return InterviewStage.entries.firstOrNull { it.name == value } ?: InterviewStage.UNKNOWN
    }

    // InterviewMethod converters
    @TypeConverter
    fun fromInterviewMethod(value: InterviewMethod?): String? {
        return value?.name
    }

    @TypeConverter
    fun toInterviewMethod(value: String?): InterviewMethod? {
        return value?.let { InterviewMethod.valueOf(it) }
    }

    // InterviewOutcome converters
    @TypeConverter
    fun fromInterviewOutcome(value: InterviewOutcome): String {
        return value.name
    }

    @TypeConverter
    fun toInterviewOutcome(value: String): InterviewOutcome {
        return InterviewOutcome.entries.firstOrNull { it.name == value } ?: InterviewOutcome.UNKNOWN
    }
}
