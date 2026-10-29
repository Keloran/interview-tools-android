package tools.interviews.android.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.data.api.InterviewRequests
import java.time.LocalDate

class InterviewStateTest {
    private fun row() = Interview(jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.FIRST_STAGE,
        applicationDate = LocalDate.of(2026, 1, 1))
    @Test fun `ghosted applications remain closed and serialize as NO_RESPONSE`() {
        val interview = row().copy(outcome = InterviewOutcome.NO_RESPONSE)
        assertTrue(interview.outcome.isClosed)
        assertFalse(interview.isUpcomingRecord)
        assertEquals("No response", interview.outcomeLabel)
        assertEquals("NO_RESPONSE", InterviewRequests.update(interview).outcome)
    }
    @Test fun `unknown values display their original names and never overwrite server fields`() {
        val interview = row().copy(stage = InterviewStage.UNKNOWN, outcome = InterviewOutcome.UNKNOWN,
            serverStageName = "Future panel", serverOutcomeName = "PAUSED_BY_EMPLOYER", notes = "My edited note")
        assertEquals("Future panel", interview.stageLabel)
        assertEquals("PAUSED_BY_EMPLOYER", interview.outcomeLabel)
        assertFalse(interview.isUpcomingRecord)
        val json = JsonParser.parseString(Gson().toJson(InterviewRequests.update(interview))).asJsonObject
        assertFalse(json.has("stage"))
        assertFalse(json.has("outcome"))
        assertEquals("My edited note", json.get("notes").asString)
    }
    @Test fun `completed outcomes never return to upcoming while awaiting remains active`() {
        for (outcome in listOf(InterviewOutcome.NO_RESPONSE, InterviewOutcome.PASSED, InterviewOutcome.REJECTED,
            InterviewOutcome.WITHDREW, InterviewOutcome.OFFER_ACCEPTED, InterviewOutcome.OFFER_DECLINED, InterviewOutcome.UNKNOWN)) {
            assertFalse(outcome.name, row().copy(outcome = outcome).isUpcomingRecord)
        }
        assertTrue(row().copy(outcome = InterviewOutcome.AWAITING_RESPONSE).isUpcomingRecord)
        assertTrue(row().copy(outcome = InterviewOutcome.SCHEDULED).isUpcomingRecord)
    }
}
