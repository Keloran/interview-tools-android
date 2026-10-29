package tools.interviews.android.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

object InterviewTimes {
    fun parseInstant(value: String?): Instant? = value?.let {
        // OffsetDateTime requires an explicit offset; an old wall-clock time is not an instant.
        OffsetDateTime.parse(it).toInstant()
    }
    fun parseDay(value: String?): LocalDate? = value?.let { LocalDate.parse(it.take(10)) }
    fun local(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime = instant.atZone(zone).toLocalDateTime()
    fun validInstants(local: LocalDateTime, zone: ZoneId): List<Instant> =
        zone.rules.getValidOffsets(local).map { local.toInstant(it) }
}

val Interview.hasMeeting: Boolean get() = stage !in setOf(InterviewStage.APPLIED, InterviewStage.OFFER, InterviewStage.TECHNICAL_TEST) && (stage != InterviewStage.UNKNOWN || stageRecognized)
val Interview.displayInterviewDate: LocalDateTime? get() = if (hasMeeting) interviewInstant?.let { InterviewTimes.local(it) } ?: interviewDate else null
val Interview.meetingDurationMinutes: Int get() = durationMinutes?.takeIf { it in 15..480 } ?: 60
val Interview.displayInterviewEnd: LocalDateTime? get() = if (!hasMeeting) null else
    interviewInstant?.plusSeconds(meetingDurationMinutes * 60L)?.let { InterviewTimes.local(it) }
        ?: displayInterviewDate?.plusMinutes(meetingDurationMinutes.toLong())
fun Interview.meetingRange(format: java.time.format.DateTimeFormatter): String? = displayInterviewDate?.let {
    "${it.format(format)} – ${displayInterviewEnd?.format(format)}" + if (needsTimeConfirmation) " · Confirm timezone" else ""
}
val Interview.displayDeadline: LocalDateTime? get() = when (stage) {
    InterviewStage.OFFER -> offerRespondBy?.atStartOfDay() ?: deadline
    InterviewStage.TECHNICAL_TEST -> deadlineInstant?.let { InterviewTimes.local(it) } ?: deadline
    else -> null
}
val Interview.needsTimeConfirmation: Boolean get() = (hasMeeting && interviewInstant == null && interviewDate != null) ||
    (stage == InterviewStage.TECHNICAL_TEST && deadlineInstant == null && deadline != null)

fun Interview.meetingWireValue(): String? {
    if (!hasMeeting) return null
    check(interviewInstant != null || interviewDate == null) { "Open Edit and confirm the older interview time and timezone before syncing." }
    return interviewInstant?.toString()
}
fun Interview.deadlineWireValue(): String? {
    if (stage == InterviewStage.OFFER) return (offerRespondBy ?: deadline?.toLocalDate())?.toString()?.plus("T00:00:00Z")
    if (stage != InterviewStage.TECHNICAL_TEST) return null
    check(deadlineInstant != null || deadline == null) { "Open Edit and confirm the older test deadline and timezone before syncing." }
    return deadlineInstant?.toString()
}
