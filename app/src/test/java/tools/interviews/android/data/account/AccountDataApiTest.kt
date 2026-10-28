package tools.interviews.android.data.account

import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountDataApiTest {
    @get:Rule val temporary = TemporaryFolder()
    private var request: Request? = null
    private val factory = Call.Factory { sent ->
        request = sent
        mockk<Call>().also { call -> every { call.execute() } answers {
            Response.Builder().request(sent).protocol(Protocol.HTTP_1_1).code(code).message("result")
                .body(payload.toResponseBody(contentType.toMediaType())).build()
        } }
    }
    private var code = 200
    private var payload = "company,notes\nAcme,\"Multiline\nnotes\"\n"
    private var contentType = "text/csv; charset=utf-8"

    @Test fun `delete sends authenticated JSON confirmation and accepts the empty 204 response`() = runTest {
        code = 204
        HttpAccountDataApi(factory).deleteAccount("secret", "delete my account")
        assertEquals("DELETE", request!!.method)
        assertEquals("https://interviews.tools/api/account", request!!.url.toString())
        assertEquals("Bearer secret", request!!.header("Authorization"))
        val buffer = Buffer()
        request!!.body!!.writeTo(buffer)
        assertEquals("{\"confirm\":\"delete my account\"}", buffer.readUtf8())
    }

    @Test fun `partial server deletion message is preserved for retry`() = runTest {
        code = 502
        payload = "{\"message\":\"Your data is deleted, but login removal failed. Please try again.\"}"
        val error = runCatching { HttpAccountDataApi(factory).deleteAccount("secret", "delete my account") }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("login removal failed"))
    }

    @Test fun `unauthorized response requests reauthentication`() = runTest {
        code = 401
        assertTrue(runCatching { HttpAccountDataApi(factory).export("expired", ExportKind.STORIES, temporary.root) }
            .exceptionOrNull()!!.message!!.contains("sign in again"))
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun `applications and stories are downloaded as separate authenticated CSV files`() = runTest {
        val api = HttpAccountDataApi(factory)
        val applications = api.export("secret", ExportKind.APPLICATIONS, temporary.root)
        assertEquals("applications", request!!.url.queryParameter("kind"))
        assertEquals("Bearer secret", request!!.header("Authorization"))
        assertFalse(request!!.url.toString().contains("secret"))
        val stories = api.export("secret", ExportKind.STORIES, temporary.root)
        assertEquals("stories", request!!.url.queryParameter("kind"))
        assertNotEquals(applications, stories)
        assertEquals(payload, applications.readText())
        assertEquals(payload, stories.readText())
    }

    @Test fun `unexpected HTML and redirects are never shared as CSV`() = runTest {
        contentType = "text/html"
        assertTrue(runCatching { HttpAccountDataApi(factory).export("secret", ExportKind.STORIES, temporary.root) }.isFailure)
        code = 302
        assertTrue(runCatching { HttpAccountDataApi(factory).export("secret", ExportKind.APPLICATIONS, temporary.root) }.isFailure)
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
}
