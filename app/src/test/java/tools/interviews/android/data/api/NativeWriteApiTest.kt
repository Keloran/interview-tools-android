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
    private val factory = Call.Factory { sent ->
        requests += sent
        mockk<Call>(relaxed = true).also { call ->
            every { call.request() } returns sent
            every { call.enqueue(any()) } answers {
                val payload = if (status == 200) """{"id":10,"jobTitle":"Engineer","company":{"id":1,"name":"Example"},"applicationDate":"2026-01-01","outcome":"AWAITING_RESPONSE"}""" else """{"message":"Not found"}"""
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
    @Test fun `older servers never receive a fallback create at the unsafe legacy URL`() = runTest {
        status = 404
        val api = APIService.forTesting("https://example.test/api/", factory)
        val row = Interview(jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1))
        assertTrue(runCatching { api.createInterview(InterviewRequests.create(row)) }.isFailure)
        assertEquals(listOf("/api/native/interview"), requests.map { it.url.encodedPath })
    }
}
