package tools.interviews.android.data.api

import com.google.gson.JsonObject
import java.net.URI

data class SharedListing(val title: String, val link: String, val description: String, val truncated: Boolean)
object SharedListings {
    fun parse(text: String?, subject: String?): SharedListing {
        val raw = text.orEmpty()
        val bounded = raw.take(100_000)
        val link = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(bounded)?.value?.trimEnd('.', ',', ')', ']', ';').orEmpty()
        val validLink = runCatching { URI(link).let { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null && link.length <= 2000 } }.getOrDefault(false)
        val url = if (validLink) link else ""
        return SharedListing(subject.orEmpty().take(300), url, if (bounded.trim() == url) "" else bounded, raw.length > 100_000)
    }
}
data class JobCheckInput(val link: String, val title: String, val company: String, val client: String, val description: String) {
    fun json(): JsonObject {
        require(company.isNotBlank() || client.isNotBlank() || link.isNotBlank()) { "Enter a company, end client or job URL to match your history." }
        require(title.length <= 300 && company.length <= 300 && client.length <= 300 && link.length <= 2000 && description.length <= 100_000) { "Shorten the fields before checking." }
        if (link.isNotBlank()) require(runCatching { URI(link).let { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)) { "Use an HTTP or HTTPS job URL." }
        return JsonObject().also { json ->
            for ((key, value) in listOf("link" to link, "title" to title, "company" to company, "client" to client, "description" to description)) json.addProperty(key, value)
        }
    }
}
private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
data class PreviousJob(val id: Int, val title: String, val company: String, val client: String, val applied: String, val status: String, val samePosting: Boolean, val sameTitle: Boolean)
data class JobWarning(val title: String, val explanation: String, val evidence: String)
data class JobCheckResult(val matches: List<PreviousJob>, val warnings: List<JobWarning>) {
    companion object {
        fun parse(json: JsonObject) = JobCheckResult(
            json.getAsJsonArray("matches").map { item -> item.asJsonObject.let {
                PreviousJob(it.get("id").asInt, it.text("jobTitle"), it.text("companyName"), it.text("clientCompany"), it.text("applicationDate").take(10), it.text("status"), it.get("samePosting")?.asBoolean == true, it.get("sameTitle")?.asBoolean == true)
            } },
            json.getAsJsonArray("flags").map { item -> item.asJsonObject.let { JobWarning(it.text("title"), it.text("explanation"), it.text("evidence")) } })
    }
}
