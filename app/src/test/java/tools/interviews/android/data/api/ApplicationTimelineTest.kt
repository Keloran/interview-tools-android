package tools.interviews.android.data.api

import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.TimeZone

class ApplicationTimelineTest {
    @Test fun parentFirstHistoryRetainsOffersUnknownOutcomesAndActivity() {
        val parsed = ApplicationTimeline.parse("""{
          "id":7,"applicationDate":"2026-01-01T00:00:00Z","jobTitle":"Engineer","firstResponseAt":null,
          "interviews":[
            {"id":3,"parentId":2,"stage":{"stage":"Offer"},"outcome":"FUTURE_OUTCOME","date":"2099-01-01T00:00:00Z","deadline":"2026-02-01T00:00:00Z","created_at":"2026-01-01T00:00:00Z","stageMethod":null},
            {"id":2,"parentId":null,"stage":{"stage":"Technical"},"outcome":"NO_RESPONSE","date":"2026-01-20T12:00:00Z","created_at":"2026-01-02T00:00:00Z","stageMethod":{"method":"Teams"},"interviewer":"Alex"}],
          "followUps":[{"id":8,"sentAt":"2026-01-12T00:00:00Z"}],
          "availabilityLinks":[{"id":"uuid-link","created_at":"2026-01-03T00:00:00Z","stage":{"stage":"Technical"},"firstViewedAt":"2026-01-04T00:00:00Z","bookedSlot":"2026-01-20T12:00:00Z"}]
        }""")
        assertEquals(listOf("applied", "stage-2", "stage-3", "link-uuid-link", "opened-uuid-link", "follow-up-8", "booked-uuid-link"), parsed.events.map { it.id })
        assertTrue(parsed.events[1].detail.contains("No response"))
        assertTrue(parsed.events[1].detail.contains("Teams"))
        assertEquals("2026-02-01T00:00:00Z", parsed.events[2].date)
        assertTrue(parsed.events[2].detail.contains("FUTURE_OUTCOME"))
        assertNull(parsed.firstResponseAt)
    }
    @Test fun cyclesDisconnectedHistoryAndMissingReplyRemainVisibleWithoutEstimates() {
        val parsed = ApplicationTimeline.parse("""{"id":7,"applicationDate":"2026-01-01","interviews":[
          {"id":1,"parentId":2,"created_at":"2026-01-02","stage":{"stage":"Custom"}},
          {"id":2,"parentId":1,"created_at":"2026-01-03","stage":{"stage":"Other"}},
          {"id":3,"parentId":99,"created_at":"2026-01-04","stage":{"stage":"Disconnected"}}]}""")
        assertEquals(4, parsed.events.size)
        assertNull(parsed.firstResponseAt)
    }
    @Test fun endTimeUsesElapsedDurationAcrossDstAndDefaultsToSixty() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val row = Interview(jobTitle="Engineer", companyName="Example", stage=InterviewStage.FIRST_STAGE,
                applicationDate=LocalDate.of(2026,1,1), interviewInstant=Instant.parse("2026-03-29T00:30:00Z"))
            assertEquals(LocalDateTime.parse("2026-03-29T02:30"), row.displayInterviewEnd)
            assertEquals(LocalDateTime.parse("2026-03-29T03:00"), row.copy(durationMinutes=90).displayInterviewEnd)
            assertNull(row.copy(stage=InterviewStage.OFFER).displayInterviewEnd)
            assertNull(row.copy(stage=InterviewStage.OFFER).meetingRange(DateTimeFormatter.ISO_DATE_TIME))
        } finally { TimeZone.setDefault(previous) }
    }
}
