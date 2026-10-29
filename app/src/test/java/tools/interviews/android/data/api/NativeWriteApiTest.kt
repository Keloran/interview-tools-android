package tools.interviews.android.data.api

import com.google.gson.JsonParser
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import tools.interviews.android.model.*
import java.time.LocalDate

class NativeWriteApiTest {
    private val requests = mutableListOf<Request>()
    private var status = 200
    private var responseBody: String? = null
    private val factory = Call.Factory { sent ->
        requests += sent
        mockk<Call>(relaxed = true).also { call ->
            every { call.request() } returns sent
            every { call.enqueue(any()) } answers {
                val payload = responseBody ?: if (status == 200) """{"id":10,"jobTitle":"Engineer","company":{"id":1,"name":"Example"},"applicationDate":"2026-01-01","outcome":"AWAITING_RESPONSE"}""" else """{"message":"Not found"}"""
                firstArg<Callback>().onResponse(call, Response.Builder().request(sent).protocol(Protocol.HTTP_1_1)
                    .code(status).message("result").body(payload.toResponseBody("application/json".toMediaType())).build())
            }
        }
    }
    @Test fun `retry-safe creates use the supporting endpoint and retain their mutation ID`() = runTest {
        val api = APIService.forTesting("https://example.test/api/", factory)
        val draft = Interview(jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.APPLIED,
            applicationDate = LocalDate.of(2026, 1, 1), clientMutationId = "11111111-1111-4111-8111-111111111111")
        api.createInterview(InterviewRequests.create(draft))
        val sent = requests.single()
        assertEquals("/api/native/interview", sent.url.encodedPath)
        val body = Buffer().also { sent.body!!.writeTo(it) }.readUtf8()
        assertEquals(draft.clientMutationId, JsonParser.parseString(body).asJsonObject.get("clientMutationId").asString)
    }
    @Test fun `JSON patch sends explicit nulls while leaving unknown fields absent`() = runTest {
        val api = APIService.forTesting("https://example.test/api/", factory)
        api.patchInterview(10, JsonParser.parseString("{\"notes\":\"\",\"link\":null}").asJsonObject)
        val sent = requests.single()
        assertEquals("/api/native/interview/10", sent.url.encodedPath)
        val body = JsonParser.parseString(Buffer().also { sent.body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertTrue(body.get("link").isJsonNull)
        assertEquals("", body.get("notes").asString)
        assertFalse(body.has("date"))
        assertFalse(body.has("outcome"))
    }
    @Test fun `application history and first reply use owned application endpoints with explicit clears`() = runTest {
        val api = APIService.forTesting("https://example.test/api/", factory)
        api.timeline(77)
        api.firstReply(77, JsonParser.parseString("{\"firstResponseAt\":null}").asJsonObject)
        assertEquals(listOf("/api/applications/77/timeline", "/api/applications/77/first-response"), requests.map { it.url.encodedPath })
        assertEquals("PUT", requests.last().method)
        val body = JsonParser.parseString(Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertTrue(body.get("firstResponseAt").isJsonNull)
    }
    @Test fun `job checks post editable listing fields to the shared API`() = runTest {
        val api = APIService.forTesting("https://example.test/api/", factory)
        api.checkJob(JobCheckInput("https://jobs.example.com/123", "Engineer", "Agency", "Employer", "Listing text").json())
        val sent = requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/api/check", sent.url.encodedPath)
        val body = JsonParser.parseString(Buffer().also { sent.body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals("Employer", body.get("client").asString)
        assertEquals("Listing text", body.get("description").asString)
    }
    @Test fun `recruiter directory and contact clears use the application contract`() = runTest {
        val api = APIService.forTesting("https://example.test/api/", factory)
        responseBody = "[]"
        assertEquals(0, api.recruiters().size())
        assertEquals("/api/recruiters", requests.last().url.encodedPath)
        responseBody = "{\"saved\":true}"
        api.updateRecruiter(77, JsonParser.parseString(RecruiterContact(false).json()).asJsonObject)
        val sent = requests.last()
        assertEquals("/api/applications/77/recruiter", sent.url.encodedPath)
        assertEquals("PUT", sent.method)
        val body = JsonParser.parseString(Buffer().also { sent.body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertTrue(body.get("recruiterEmail").isJsonNull)
        assertFalse(body.get("viaRecruiter").asBoolean)
    }
    @Test fun `unconfirmed recruiter replies leave the update unacknowledged`() = runTest {
        responseBody = "{}"
        val api = APIService.forTesting("https://example.test/api/", factory)
        assertTrue(runCatching { api.updateRecruiter(77, JsonParser.parseString(RecruiterContact(true,"Sam").json()).asJsonObject) }.isFailure)
    }
    @Test fun `debrief feedback and preparation send explicit nested clears`() = runTest {
        val api=APIService.forTesting("https://example.test/api/",factory)
        api.patchInterview(10,JsonParser.parseString("{\"debrief\":{\"rating\":null,\"questionsAsked\":null},\"feedback\":{\"type\":null,\"text\":null}}").asJsonObject)
        val patch=JsonParser.parseString(Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertTrue(patch.getAsJsonObject("debrief").get("rating").isJsonNull)
        assertTrue(patch.getAsJsonObject("feedback").get("type").isJsonNull)
        responseBody="{\"notes\":\"\",\"storyIds\":[]}"
        api.updatePrep(10,JsonParser.parseString(StageReflection.prep("",emptyList())).asJsonObject)
        assertEquals("/api/interview/10/prep",requests.last().url.encodedPath)
        assertEquals("PUT",requests.last().method)
    }
    @Test fun `story creates use the retry safe route and never fall back`() = runTest {
        val api=APIService.forTesting("https://example.test/api/",factory)
        val body=StoryFields.body(mapOf("title" to "Example"),"").apply { addProperty("clientMutationId","11111111-1111-4111-8111-111111111111") }
        status=404
        assertTrue(runCatching { api.createStory(body) }.isFailure)
        assertEquals(listOf("/api/native/stories"),requests.map { it.url.encodedPath })
        val sent=JsonParser.parseString(Buffer().also { requests.single().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(body.get("clientMutationId"),sent.get("clientMutationId"))
    }
    @Test fun `native follow ups retain their mutation identity and avoid legacy fallback`() = runTest {
        val api=APIService.forTesting("https://example.test/api/",factory)
        val body=JsonParser.parseString("{\"applicationId\":7,\"clientMutationId\":\"11111111-1111-4111-8111-111111111111\"}").asJsonObject
        status=404
        assertTrue(runCatching { api.recordFollowUp(body) }.isFailure)
        assertEquals(listOf("/api/native/follow-ups"),requests.map { it.url.encodedPath })
        val sent=JsonParser.parseString(Buffer().also { requests.single().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(7,sent.get("applicationId").asInt)
        assertEquals(body.get("clientMutationId"),sent.get("clientMutationId"))
    }
    @Test fun `older servers never receive a fallback create at the unsafe legacy URL`() = runTest {
        status = 404
        val api = APIService.forTesting("https://example.test/api/", factory)
        val row = Interview(jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1))
        assertTrue(runCatching { api.createInterview(InterviewRequests.create(row)) }.isFailure)
        assertEquals(listOf("/api/native/interview"), requests.map { it.url.encodedPath })
    }
}
