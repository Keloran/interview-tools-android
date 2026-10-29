package tools.interviews.android.data.api

import com.google.gson.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.*
import tools.interviews.android.data.account.*

object StoryFields {
    val names = listOf("title", "situation", "task", "action", "result")

    fun body(values: Map<String, String>, tags: String): JsonObject =
        JsonObject().apply {
            val title = values["title"].orEmpty().trim()
            require(title.isNotBlank() && title.length <= 200) {
                "Enter a title (up to 200 characters)."
            }
            addProperty("title", title)
            for (field in names.drop(1)) {
                val value = values[field].orEmpty().trim()
                require(value.length <= 8000) {
                    "Use at most 8,000 characters for each STAR field."
                }
                addProperty(field, value)
            }
            val values =
                tags.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            require(values.size <= 12 && values.all { it.length <= 40 }) {
                "Use at most 12 tags, each up to 40 characters."
            }
            add("tags", JsonArray().apply { values.forEach { add(it) } })
        }
}

interface StoryWrites {
    suspend fun createStory(body: JsonObject): JsonObject

    suspend fun updateStory(id: Int, body: JsonObject)
}

/** Store the create identity before HTTP and the server ID before any follow-up edit. */
class StorySaveService(
    private val dao: ToolDraftDao,
    private val gate: AccountOperationGate,
    private val remote: StoryWrites,
    private val ownerNow: () -> String,
    private val authenticate: suspend (String) -> Unit,
) {
    private fun check(owner: String) {
        if (ownerNow() != owner) throw CancellationException("Account changed.")
    }

    suspend fun save(owner: String, key: String, requestedId: Int?, body: JsonObject): Int =
        gate.run {
            check(owner)
            require(owner != AccountOwners.GUEST) {
                "Sign in to save STAR stories to your account."
            }
            val old = dao.get(owner, key)?.json?.let { JsonParser.parseString(it).asJsonObject }
            var serverId = old?.get("serverId")?.takeUnless { it.isJsonNull }?.asInt ?: requestedId
            val mutation = old?.get("mutationId")?.asString ?: UUID.randomUUID().toString()
            fun snapshot() =
                JsonObject()
                    .apply {
                        add("body", body.deepCopy())
                        addProperty("mutationId", mutation)
                        addProperty("serverId", serverId)
                    }
                    .toString()
            dao.put(ToolDraft(owner, key, snapshot()))
            if (serverId == null) {
                authenticate(owner)
                check(owner)
                val response =
                    remote.createStory(
                        body.deepCopy().apply { addProperty("clientMutationId", mutation) }
                    )
                check(owner)
                serverId =
                    response.get("id")?.asInt?.takeIf { it > 0 }
                        ?: error("Story save wasn’t confirmed. Your draft is still saved.")
                dao.put(ToolDraft(owner, key, snapshot()))
            }
            // A replay can return an earlier create payload; always apply the current draft.
            authenticate(owner)
            check(owner)
            remote.updateStory(requireNotNull(serverId), body)
            check(owner)
            dao.delete(owner, key)
            requireNotNull(serverId)
        }
}
