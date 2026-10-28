package tools.interviews.android.data.api

import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.model.*
import java.time.LocalDate

class InterviewRequestsTest {
    private fun draft() = Interview(jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.APPLIED,
        applicationDate = LocalDate.of(2026, 1, 1), outcome = InterviewOutcome.AWAITING_RESPONSE)
    @Test fun `new applications omit previous stage while next stages send the server identity`() {
        assertNull(InterviewRequests.create(draft()).previousInterviewId)
        val next = draft().copy(previousLocalId = 42, durationMinutes = 45)
        assertEquals(900, InterviewRequests.create(next, previousServerId = 900).previousInterviewId)
        assertEquals(45, InterviewRequests.create(next, previousServerId = 900).durationMinutes)
        assertTrue(runCatching { InterviewRequests.create(next) }.isFailure)
    }
    @Test fun `catalog selections preserve actual stage and meeting method IDs`() {
        val draft = draft().copy(stage = InterviewStage.UNKNOWN, stageRecognized = true,
            serverStageId = 30, serverStageName = "Architecture review", stageSelectionChanged = true,
            serverMethodId = 50, serverMethodName = "Teams", methodSelectionChanged = true)
        assertEquals("Architecture review", InterviewRequests.create(draft).stage)
        assertEquals(50, InterviewRequests.create(draft).stageMethodId)
        assertEquals(30, InterviewRequests.update(draft).stageId)
        assertEquals(50, InterviewRequests.update(draft).stageMethodId)
        // Reading an unfamiliar stage from the server alone is never permission to rewrite it.
        assertNull(InterviewRequests.update(draft.copy(stageSelectionChanged = false)).stageId)
        assertNull(InterviewRequests.update(draft.copy(stageSelectionChanged = false)).stage)
    }
    @Test fun `unknown noncatalog stages cannot become new applications`() {
        assertTrue(runCatching { InterviewRequests.create(draft().copy(stage = InterviewStage.UNKNOWN, serverStageName = "Future")) }.isFailure)
    }
    @Test fun `meeting services retain their video controls and unknown names stay identifiable`() {
        for (name in listOf("Zoom", "Teams", "Google Meet", "Webex", "Link")) assertEquals(InterviewMethod.VIDEO_CALL, InterviewCatalog.methodKind(name))
        assertNull(InterviewCatalog.methodKind("A future service"))
        assertEquals("A future service", draft().copy(serverMethodName = "A future service").methodLabel)
    }
    @Test fun `invalid server parent IDs are rejected instead of creating an unrelated application`() {
        for (id in listOf(0, -1)) assertTrue(runCatching { InterviewRequests.create(draft(), previousServerId = id) }.isFailure)
    }
    @Test fun `invalid durations are rejected before upload`() {
        for (minutes in listOf(0, 14, 481)) assertTrue(runCatching { InterviewRequests.create(draft().copy(durationMinutes = minutes)) }.isFailure)
    }
}
