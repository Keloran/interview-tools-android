package tools.interviews.android.data.account

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

interface AccountDataApi {
    suspend fun deleteAccount(token: String, confirmation: String)
    suspend fun export(token: String, kind: ExportKind, directory: File): File
}

enum class ExportKind(val parameter: String) { APPLICATIONS("applications"), STORIES("stories") }

/** No HTTP logging: responses include private notes and stories. Never follow authenticated redirects. */
class HttpAccountDataApi(
    private val calls: Call.Factory = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://interviews.tools/api"
) : AccountDataApi {
    override suspend fun deleteAccount(token: String, confirmation: String) = withContext(Dispatchers.IO) {
        val json = Gson().toJson(mapOf("confirm" to confirmation))
        val request = Request.Builder().url("$baseUrl/account")
            .header("Authorization", "Bearer $token")
            .delete(json.toRequestBody("application/json".toMediaType())).build()
        calls.newCall(request).execute().use { response ->
            if (response.code != 204) throw IOException(errorMessage(response.code, response.body?.string()))
        }
    }

    override suspend fun export(token: String, kind: ExportKind, directory: File): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/data/export?kind=${kind.parameter}")
            .header("Authorization", "Bearer $token").get().build()
        calls.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(errorMessage(response.code, response.body?.string()))
            val body = response.body ?: throw IOException("The export was empty. Please try again.")
            if (body.contentType()?.let { it.type == "text" && it.subtype == "csv" } != true) {
                throw IOException("The server did not return a CSV export. Please try again.")
            }
            check(directory.isDirectory || directory.mkdirs()) { "Could not create the export folder." }
            // A unique file prevents a second export from replacing a file another app is reading.
            val file = File(directory, "interview-tools-${kind.parameter}-${UUID.randomUUID()}.csv")
            try {
                body.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 25L * 1024 * 1024) throw IOException("Export is too large to share here. Export from the website instead.")
                        output.write(buffer, 0, read)
                    }
                } }
                file
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }
    }

    private fun errorMessage(code: Int, body: String?): String {
        if (code == 401) return "Your session expired. Please sign in again before retrying."
        return runCatching { Gson().fromJson(body, ServerError::class.java)?.message }.getOrNull()
            ?: "The request failed (HTTP $code). Please try again."
    }
    private data class ServerError(val message: String?)
}
