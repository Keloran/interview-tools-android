package tools.interviews.android.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class JobCheckTest {
    @Test fun sharedUrlIsExtractedWithoutPretendingItContainsListingText() {
        val listing = SharedListings.parse("https://jobs.example.com/123?source=mobile", "Engineer")
        assertEquals("https://jobs.example.com/123?source=mobile", listing.link)
        assertEquals("Engineer", listing.title)
        assertEquals("", listing.description)
    }
    @Test fun sharedTextRetainsEvidenceAndIsBoundedBeforeSending() {
        val listing = SharedListings.parse("Engineer role\nhttps://jobs.example.com/123.\nUnpaid take-home task." + "x".repeat(100_000), "a".repeat(301))
        assertEquals("https://jobs.example.com/123", listing.link)
        assertTrue(listing.description.contains("Unpaid take-home"))
        assertEquals(100_000, listing.description.length)
        assertEquals(300, listing.title.length)
        assertTrue(listing.truncated)
        assertEquals("", SharedListings.parse("javascript:alert(1)", null).link)
        assertEquals("", SharedListings.parse("https://user:secret@example.com/job", null).link)
    }
    @Test fun requestUsesWebsiteFieldsAndRejectsUnmatchableOrUnsafeInput() {
        val input = JobCheckInput("", "Engineer", "Agency", "Client", "No salary")
        val json = input.json()
        assertEquals("Client", json.get("client").asString)
        assertEquals("No salary", json.get("description").asString)
        assertTrue(runCatching { input.copy(company="", client="").json() }.isFailure)
        assertTrue(runCatching { input.copy(link="file:///etc/passwd").json() }.isFailure)
        assertTrue(runCatching { input.copy(title="x".repeat(301)).json() }.isFailure)
    }
    @Test fun matchingFlagsAndUnknownFeedbackKeepTheServersMeaning() {
        val result = JobCheckResult.parse(JsonParser.parseString("""{"matches":[{"id":7,"jobTitle":"Senior Engineer","companyName":"Agency","clientCompany":"Client","applicationDate":"2026-01-01T00:00:00Z","status":"No response (closed as ghosted)","sameTitle":true,"samePosting":false}],"flags":[{"title":"No salary amount found","explanation":"Ask for the range","evidence":"Unpaid assessment"}]}""").asJsonObject)
        assertTrue(result.matches.single().sameTitle)
        assertEquals("Client", result.matches.single().client)
        assertTrue(result.matches.single().status.contains("ghosted"))
        assertEquals("Unpaid assessment", result.warnings.single().evidence)
    }
}
