package tools.interviews.android.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.model.*
import java.time.LocalDate
import java.time.Instant

class InterviewChangesTest {
    private fun row() = Interview(id = 1, ownerId = "account-a", serverId = 10, companyName = "Example",
        jobTitle = "Engineer", stage = InterviewStage.FIRST_STAGE, applicationDate = LocalDate.of(2026, 1, 1),
        interviewInstant = Instant.parse("2026-06-01T09:00:00Z"), notes = "Old notes", link = "https://example.com")
    @Test fun `notes-only changes omit untouched state and scheduling fields`() {
        val patch = InterviewChanges.diff(row(), row().copy(notes = "New notes"))
        assertEquals(setOf("notes"), patch.keySet())
        assertEquals("New notes", patch.get("notes").asString)
    }
    @Test fun `explicit clears remain different from omitted fields`() {
        val patch = InterviewChanges.diff(row(), row().copy(notes = null, link = null, interviewer = "Jane"))
        assertTrue(patch.get("link").isJsonNull)
        assertEquals("", patch.get("notes").asString)
        assertFalse(patch.has("date"))
        assertTrue(patch.toString().contains("\"link\":null"))
    }
    @Test fun `a stale edit preserves newer untouched server values and identities`() {
        val before = row()
        val current = before.copy(jobTitle = "Promoted title", outcome = InterviewOutcome.NO_RESPONSE, serverId = 900, applicationId = 44)
        val result = InterviewChanges.apply(current, before, before.copy(notes = "My new notes"))
        assertEquals("Promoted title", result.jobTitle)
        assertEquals(InterviewOutcome.NO_RESPONSE, result.outcome)
        assertEquals(900, result.serverId)
        assertEquals(44, result.applicationId)
        assertEquals("My new notes", result.notes)
        assertEquals(setOf("notes"), InterviewChanges.diff(current, result).keySet())
    }
    @Test fun `queued changes merge last edits without dropping independent clears`() {
        val previous = "{\"notes\":\"First\",\"link\":null}"
        val next = JsonParser.parseString("{\"notes\":\"Latest\",\"interviewer\":\"Jane\"}").asJsonObject
        val merged = JsonParser.parseString(InterviewChanges.merge(previous, next)).asJsonObject
        assertEquals("Latest", merged.get("notes").asString)
        assertTrue(merged.get("link").isJsonNull)
        assertEquals("Jane", merged.get("interviewer").asString)
    }
    @Test fun `unknown server state is never substituted in queued notes edits`() {
        val unknown = row().copy(stage = InterviewStage.UNKNOWN, outcome = InterviewOutcome.UNKNOWN,
            serverStageName = "Future panel", serverOutcomeName = "PAUSED")
        assertEquals(setOf("notes"), InterviewChanges.diff(unknown, unknown.copy(notes = "Edit")).keySet())
    }
    @Test fun `older unconfirmed schedules do not block unrelated notes edits`() {
        val old = row().copy(interviewInstant = null, interviewDate = java.time.LocalDateTime.of(2026, 6, 1, 9, 0))
        assertEquals(setOf("notes"), InterviewChanges.diff(old, old.copy(notes = "New")).keySet())
    }
    @Test fun `offers keep their initial received state without an artificial scheduled update`() {
        assertEquals(0, InterviewChanges.afterCreate(row().copy(stage = InterviewStage.OFFER, outcome = InterviewOutcome.OFFER_RECEIVED)).size())
    }
}
