package tools.interviews.android.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.*
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.account.AccountOwners
import java.time.*
import java.time.format.DateTimeFormatter

private fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
private fun JsonObject.objects(key: String) = getAsJsonArray(key)?.map { it.asJsonObject }.orEmpty()
private fun JsonObject.name(key: String, field: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.string(field).orEmpty()

data class TimelineEvent(val id: String, val date: String, val title: String, val detail: String, val dateOnly: Boolean = false)
data class ApplicationTimeline(val id: Int, val firstResponseAt: String?, val events: List<TimelineEvent>) {
    companion object {
        fun parse(json: String): ApplicationTimeline {
            val app = JsonParser.parseString(json).asJsonObject
            val events = mutableListOf(TimelineEvent("applied", app.string("applicationDate").orEmpty(), "Applied", app.string("jobTitle").orEmpty(), true))
            // Parent-first ordering, retaining disconnected records and malformed cycles.
            val pending = app.objects("interviews").sortedWith(compareBy({ it.string("created_at") }, { it.get("id").asInt })).toMutableList()
            val ids = pending.map { it.get("id").asInt }.toSet()
            val visited = mutableSetOf<Int>()
            while (pending.isNotEmpty()) {
                val index = pending.indexOfFirst { it.string("parentId")?.toIntOrNull().let { parent -> parent == null || parent !in ids || parent in visited } }
                val stage = pending.removeAt(index.coerceAtLeast(0))
                val id = stage.get("id").asInt
                visited += id
                val name = stage.name("stage", "stage")
                val offer = name.equals("Offer", true)
                val date = if (offer) stage.string("deadline") ?: stage.string("created_at") else if (name.equals("Applied", true)) app.string("applicationDate") else stage.string("date") ?: stage.string("deadline") ?: stage.string("created_at")
                val outcome = stage.string("outcome")?.let { raw -> tools.interviews.android.model.InterviewOutcome.entries.firstOrNull { it.name == raw }?.displayName ?: raw }
                val dateLabel = if (offer && stage.string("deadline") != null) "Respond by" else if (offer) "Offer recorded" else if (stage.string("date") != null) "Scheduled" else "Recorded"
                val detail = listOfNotNull(outcome, stage.name("stageMethod", "method").takeIf { it.isNotBlank() }, stage.string("interviewer"), stage.string("nextStep")?.let { "Next: $it" }).joinToString(" · ")
                events += TimelineEvent("stage-$id", date.orEmpty(), "$name · $dateLabel", detail, offer || name.equals("Applied", true))
            }
            val activity = app.objects("followUps").map { TimelineEvent("follow-up-${it.get("id")}", it.string("sentAt").orEmpty(), "Follow-up recorded", "You recorded that you chased this application.") }.toMutableList()
            for (link in app.objects("availabilityLinks")) {
                val id = link.get("id").asString
                val stage = link.name("stage", "stage")
                activity += TimelineEvent("link-$id", link.string("created_at").orEmpty(), "Availability link created", "For $stage. Creating a link does not confirm it was sent.")
                link.string("firstViewedAt")?.let { activity += TimelineEvent("opened-$id", it, "Availability link first opened", "For $stage.") }
                link.string("bookedSlot")?.let { activity += TimelineEvent("booked-$id", it, "Booked interview slot", "For $stage. This is the interview time.") }
            }
            events += activity.sortedWith(compareBy({ it.date }, { it.id }))
            return ApplicationTimeline(app.get("id").asInt, app.string("firstResponseAt"), events)
        }
    }
}

interface ApplicationTimelineRemote {
    suspend fun timeline(id: Int): JsonObject
    suspend fun firstReply(id: Int, body: JsonObject): JsonObject
}

/** Cache and network replies are tied to the initiating account, including after await. */
class ApplicationHistory(
    private val dao: ApplicationTimelineDao,
    private val remote: ApplicationTimelineRemote,
    private val gate: AccountOperationGate,
    private val ownerNow: () -> String,
    private val authenticate: suspend (String) -> Unit
) {
    private fun check(owner: String) {
        if (owner == AccountOwners.GUEST || ownerNow() != owner) throw CancellationException("Account changed.")
    }
    suspend fun cached(owner: String, id: Int): ApplicationTimeline? = gate.run {
        check(owner)
        dao.get(owner, id)?.let { check(owner); ApplicationTimeline.parse(it.json) }
    }
    suspend fun refresh(owner: String, id: Int): ApplicationTimeline = gate.run {
        check(owner); authenticate(owner); check(owner)
        val json = remote.timeline(id)
        check(owner)
        require(json.get("id").asInt == id) { "Unexpected application response." }
        val parsed = ApplicationTimeline.parse(json.toString())
        dao.put(ApplicationTimelineCache(owner, id, json.toString()))
        parsed
    }
    suspend fun saveFirstReply(owner: String, id: Int, date: LocalDate?, applied: LocalDate, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): String? = gate.run {
        check(owner)
        require(date == null || (!date.isBefore(applied) && !date.isAfter(today))) { "Choose a date between applying and today." }
        authenticate(owner); check(owner)
        val body = JsonObject().apply { if (date == null) add("firstResponseAt", com.google.gson.JsonNull.INSTANCE) else addProperty("firstResponseAt", date.toString()) }
        val result = remote.firstReply(id, body)
        check(owner)
        require(result.has("firstResponseAt")) { "First-reply confirmation was empty." }
        val value = result.string("firstResponseAt")
        dao.get(owner, id)?.let { cached ->
            val json = JsonParser.parseString(cached.json).asJsonObject
            json.add("firstResponseAt", result.get("firstResponseAt"))
            dao.put(cached.copy(json = json.toString()))
        }
        value
    }
}

fun timelineDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
}.getOrElse { value.take(10) }
