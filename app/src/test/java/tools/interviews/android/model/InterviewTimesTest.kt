package tools.interviews.android.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class InterviewTimesTest {
    private fun interview(stage: InterviewStage = InterviewStage.FIRST_STAGE) = Interview(
        jobTitle = "Engineer", companyName = "Example", stage = stage, applicationDate = LocalDate.of(2026, 1, 1))

    @Test fun `offset and fractional timestamps round trip through UTC without changing instant`() {
        for (input in listOf("2026-06-01T09:00:00Z", "2026-06-01T10:00:00+01:00", "2026-06-01T05:00:00-04:00", "2026-06-01T14:30:00+05:30", "2026-06-01T09:00:00.123456789Z")) {
            val instant = InterviewTimes.parseInstant(input)!!
            val row = interview().copy(interviewInstant = instant)
            assertEquals(instant, InterviewTimes.parseInstant(row.meetingWireValue()))
        }
    }
    @Test fun `London summer winter and New York display the correct local clock`() {
        val cases = listOf(
            Triple("2026-06-01T09:00:00Z", "Europe/London", "2026-06-01T10:00"),
            Triple("2026-12-01T09:00:00Z", "Europe/London", "2026-12-01T09:00"),
            Triple("2026-06-01T09:00:00Z", "America/New_York", "2026-06-01T05:00"),
            Triple("2026-03-29T00:59:00Z", "Europe/London", "2026-03-29T00:59"),
            Triple("2026-03-29T01:00:00Z", "Europe/London", "2026-03-29T02:00"))
        for ((instant, zone, expected) in cases) assertEquals(LocalDateTime.parse(expected), InterviewTimes.local(Instant.parse(instant), ZoneId.of(zone)))
    }
    @Test fun `clock gaps have no valid instant and repeated hours require a choice`() {
        for ((zone, gap, repeat) in listOf(
            Triple("Europe/London", "2026-03-29T01:30", "2026-10-25T01:30"),
            Triple("America/New_York", "2026-03-08T02:30", "2026-11-01T01:30"))) {
            assertTrue(InterviewTimes.validInstants(LocalDateTime.parse(gap), ZoneId.of(zone)).isEmpty())
            val choices = InterviewTimes.validInstants(LocalDateTime.parse(repeat), ZoneId.of(zone))
            assertEquals(2, choices.size)
            assertEquals(3600L, java.time.Duration.between(choices[0], choices[1]).seconds)
        }
    }
    @Test fun `calendar days retain their stated day regardless of timestamp offset`() {
        assertEquals(LocalDate.of(2026, 1, 1), InterviewTimes.parseDay("2026-01-01T00:00:00+14:00"))
        val offer = interview(InterviewStage.OFFER).copy(offerRespondBy = LocalDate.of(2026, 1, 1), interviewInstant = Instant.EPOCH)
        assertNull(offer.meetingWireValue())
        assertNull(offer.displayInterviewDate)
        assertEquals("2026-01-01T00:00:00Z", offer.deadlineWireValue())
    }
    @Test fun `unscheduled values stay null and offsetless old times require confirmation`() {
        assertNull(InterviewTimes.parseInstant(null))
        assertNull(interview().meetingWireValue())
        val legacy = interview().copy(interviewDate = LocalDateTime.of(2026, 6, 1, 9, 0))
        assertTrue(legacy.needsTimeConfirmation)
        assertTrue(runCatching { legacy.meetingWireValue() }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { InterviewTimes.parseInstant("2026-06-01T09:00:00") }.isFailure)
        val test = interview(InterviewStage.TECHNICAL_TEST).copy(deadlineInstant = Instant.parse("2026-06-01T22:59:00Z"))
        assertEquals("2026-06-01T22:59:00Z", test.deadlineWireValue())
        assertNull(test.meetingWireValue())
    }
}
