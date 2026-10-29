package tools.interviews.android.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RecruitersTest {
    @Test fun clearContactsAreExplicitAndUnknownRatesStayUnknown() {
        val json = JsonParser.parseString(RecruiterContact(false, "Old", "", "").json()).asJsonObject
        assertFalse(json.get("viaRecruiter").asBoolean)
        assertTrue(json.get("recruiterName").isJsonNull)
        val record = RecruiterRecord.parse(JsonParser.parseString("""[{"agency":"Recruit Co","contact":null,"stats":{"roles":4,"ghosted":3,"responseRate":25,"ghostRate":75,"feedbackRate":null}}]""").asJsonArray).single()
        assertEquals("Recruit Co: 4 roles, ghosted 3", record.summary)
        assertEquals("Unknown", record.feedbackRate)
        assertEquals("25%", record.responseRate)
    }
    @Test fun validatesContactDetailsAndRestrictsExternalProfiles() {
        assertEquals("Sam", RecruiterContact.parse(RecruiterContact(true,"Sam","sam@example.com","https://www.linkedin.com/in/sam").json()).name)
        for (link in listOf("http://linkedin.com/in/sam", "https://linkedin.com.evil.test/in/sam", "javascript:alert(1)", "https://user@linkedin.com/in/sam", "https://linkedin.com:444/in/sam")) {
            assertFalse(link, RecruiterContact.safeLinkedIn(link))
            assertTrue(runCatching { RecruiterContact(true, linkedIn=link).json() }.isFailure)
        }
        assertTrue(runCatching { RecruiterContact(true, email="bad-email").json() }.isFailure)
        assertTrue(runCatching { RecruiterContact(true, name="Sam\nInjected").json() }.isFailure)
    }
    @Test fun turningOffRecruiterCanClearInvalidHistoricalDetails() {
        val json = RecruiterContact(false, email="old invalid", linkedIn="http://old.example").json()
        assertFalse(RecruiterContact.parse(json).viaRecruiter)
        assertEquals("", RecruiterContact.parse(json).email)
    }
}
