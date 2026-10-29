package tools.interviews.android.data.api

import com.google.gson.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.*
import tools.interviews.android.data.account.*

interface FollowUpWrites {
    suspend fun recordFollowUp(body: JsonObject): JsonObject
}

class FollowUpQueue(
    private val dao: ToolDraftDao,
    private val gate: AccountOperationGate,
    private val remote: FollowUpWrites,
    private val ownerNow: () -> String,
    private val authenticate: suspend (String) -> Unit,
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private fun check(owner: String) {
        if (ownerNow() != owner) throw CancellationException("Account changed.")
    }

    suspend fun enqueue(owner: String, applicationId: Int, dueAt: String): Boolean = gate.run {
        check(owner)
        require(runCatching { java.time.Instant.parse(dueAt) }.isSuccess) {
            "Refresh the due date before recording this follow-up."
        }
        require(owner != AccountOwners.GUEST && applicationId > 0) {
            "Sign in to record follow-ups."
        }
        val key = "pending:follow-up:$applicationId"
        if (dao.get(owner, key) != null) return@run false
        val acknowledged =
            dao.get(owner, "recorded:follow-up:$applicationId")?.json?.let {
                JsonParser.parseString(it).asJsonObject
            }
        if (acknowledged?.get("dueAt")?.asString == dueAt) return@run false
        val json =
            JsonObject().apply {
                addProperty("applicationId", applicationId)
                addProperty("dueAt", dueAt)
                addProperty("clientMutationId", UUID.randomUUID().toString())
            }
        check(owner)
        dao.put(ToolDraft(owner, key, json.toString()))
        true
    }

    suspend fun states(owner: String): Map<Int, JsonObject> = gate.run {
        check(owner)
        val result =
            (dao.prefix(owner, "recorded:follow-up:") + dao.prefix(owner, "pending:follow-up:"))
                .associate { draft ->
                    val json = JsonParser.parseString(draft.json).asJsonObject
                    json.addProperty("pending", draft.draftKey.startsWith("pending:"))
                    json.get("applicationId").asInt to json
                }
        check(owner)
        result
    }

    suspend fun push(owner: String): Int = gate.run { pushLocked(owner) }

    // SyncService calls this while it already holds the shared account gate.
    internal suspend fun pushLocked(owner: String): Int {
        check(owner)
        var failed = 0
        for (draft in dao.prefix(owner, "pending:follow-up:")) {
            val json = JsonParser.parseString(draft.json).asJsonObject
            try {
                authenticate(owner)
                check(owner)
                val body =
                    json.deepCopy().apply {
                        remove("dueAt")
                        remove("error")
                    }
                val response = remote.recordFollowUp(body)
                check(owner)
                require(
                    response.get("id")?.asInt?.let { it > 0 } == true && response.has("sentAt")
                ) {
                    "Follow-up confirmation was empty."
                }
                json.remove("error")
                json.add("sentAt", response.get("sentAt"))
                transaction {
                    dao.put(
                        ToolDraft(
                            owner,
                            "recorded:follow-up:${json.get("applicationId").asInt}",
                            json.toString(),
                        )
                    )
                    dao.delete(owner, draft.draftKey)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                check(owner)
                failed++
                json.addProperty("error", error.message ?: "Needs retry")
                dao.put(draft.copy(json = json.toString()))
            }
        }
        return failed
    }
}
