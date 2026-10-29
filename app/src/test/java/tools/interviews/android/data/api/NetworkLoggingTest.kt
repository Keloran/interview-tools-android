package tools.interviews.android.data.api

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class NetworkLoggingTest {
    @Test fun `release emits no network logs`() { assertTrue(runRequest(false).isEmpty()) }
    @Test fun `debug logs neither credentials nor private request or response bodies`() {
        val logs = runRequest(true).joinToString("\n")
        assertTrue(logs.contains("POST"))
        for (secret in listOf("secret-jwt", "private-cookie", "private-request", "private-response")) assertFalse(logs.contains(secret))
    }
    private fun runRequest(debug: Boolean): List<String> {
        val logs = mutableListOf<String>()
        val subject = NetworkLogging.interceptor(debug) { logs += it }
        val request = Request.Builder().url("https://interviews.tools/api/interview")
            .header("Authorization", "Bearer secret-jwt").header("Cookie", "private-cookie")
            .post("private-request".toRequestBody()).build()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("private-response".toResponseBody()).build()
        val chain = mockk<Interceptor.Chain>(relaxed = true)
        every { chain.request() } returns request
        every { chain.connection() } returns null
        every { chain.proceed(any()) } returns response
        subject.intercept(chain).close()
        return logs
    }
}
