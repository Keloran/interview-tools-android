package tools.interviews.android.data.api

import com.google.gson.*
import java.net.URI

data class RecruiterContact(val viaRecruiter: Boolean = false, val name: String = "", val email: String = "", val linkedIn: String = "") {
    fun json(): String {
        require(!viaRecruiter || name.length <= 200 && name.none { it.code < 32 || it.code == 127 }) { "Use a recruiter name without control characters (up to 200 characters)." }
        require(!viaRecruiter || email.isBlank() || (email.length <= 254 && Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(email))) { "Enter a valid recruiter email." }
        require(!viaRecruiter || linkedIn.isBlank() || safeLinkedIn(linkedIn)) { "Use an HTTPS linkedin.com profile URL." }
        return JsonObject().apply {
            addProperty("viaRecruiter", viaRecruiter)
            for ((key, value) in listOf("recruiterName" to name, "recruiterEmail" to email, "recruiterLinkedIn" to linkedIn)) {
                if (!viaRecruiter || value.isBlank()) add(key, JsonNull.INSTANCE) else addProperty(key, value)
            }
        }.toString()
    }
    companion object {
        fun parse(json: String?): RecruiterContact {
            if (json == null) return RecruiterContact()
            val obj = JsonParser.parseString(json).asJsonObject
            return RecruiterContact(obj.get("viaRecruiter")?.asBoolean == true, obj.text("recruiterName"), obj.text("recruiterEmail"), obj.text("recruiterLinkedIn"))
        }
        fun fromApi(row: APIInterview): String? = if (row.viaRecruiter == null && row.recruiterName == null && row.recruiterEmail == null && row.recruiterLinkedIn == null) null else JsonObject().apply {
            addProperty("viaRecruiter", row.viaRecruiter == true)
            addProperty("recruiterName", row.recruiterName)
            addProperty("recruiterEmail", row.recruiterEmail)
            addProperty("recruiterLinkedIn", row.recruiterLinkedIn)
        }.toString()
        fun safeLinkedIn(value: String): Boolean = runCatching {
            URI(value).let { it.scheme.equals("https", true) && it.host?.lowercase() in setOf("linkedin.com", "www.linkedin.com") && it.userInfo == null && it.port == -1 && value.length <= 2048 && value.none { ch -> ch.isWhitespace() || ch.code < 32 || ch.code == 127 } }
        }.getOrDefault(false)
    }
}
private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
data class RecruiterRecord(val agency: String, val contact: RecruiterContact?, val roles: Int, val ghosted: Int, val responseRate: String, val ghostRate: String, val feedbackRate: String) {
    val summary get() = "$agency: $roles roles, ghosted $ghosted"
    companion object {
        fun parse(array: JsonArray) = array.map { element ->
            val item = element.asJsonObject
            val stats = item.getAsJsonObject("stats")
            fun rate(key: String) = stats.get(key)?.takeUnless { it.isJsonNull }?.asInt?.let { "$it%" } ?: "Unknown"
            RecruiterRecord(item.text("agency"), item.get("contact")?.takeUnless { it.isJsonNull }?.let { RecruiterContact.parse(it.toString()) },
                stats.get("roles").asInt, stats.get("ghosted").asInt, rate("responseRate"), rate("ghostRate"), rate("feedbackRate"))
        }
    }
}
