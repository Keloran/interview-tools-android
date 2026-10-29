package tools.interviews.android.data.api

import com.google.gson.JsonParser
import java.util.UUID
import kotlinx.coroutines.CancellationException
import tools.interviews.android.data.InterviewDao
import tools.interviews.android.model.Interview

data class PendingSyncResult(val created: Int, val updated: Int, val failed: Int, val remaining: Int = 0)

/** Runs under the account gate. Every acknowledged create identity survives later failures. */
class PendingSyncEngine(
    private val dao: InterviewDao,
    private val writes: SyncWrites,
    private val currentAccount: () -> String?,
    private val authenticate: suspend (String) -> Unit = {}
) {
    private fun checkAccount(owner: String) {
        if (currentAccount() != owner) throw CancellationException("Account changed during sync.")
    }
    suspend fun sync(owner: String, onlyId: Long? = null): PendingSyncResult {
        val attempted = mutableMapOf<Long, Result<Int>>()
        var capability: Result<Unit>? = null
        var created = 0
        var updated = 0
        var failed = 0
        suspend fun record(id: Long, ancestors: Set<Long>): Int {
            attempted[id]?.let { return it.getOrThrow() }
            try {
                checkAccount(owner)
                require(id !in ancestors) { "Previous-stage chain contains a cycle." }
                var row = dao.getInterviewById(id, owner) ?: error("Interview no longer exists in this account.")
                if (row.serverId == null) {
                    if (row.clientMutationId == null) {
                        row = row.copy(clientMutationId = UUID.randomUUID().toString())
                        dao.update(row)
                    }
                    authenticate(owner)
                    checkAccount(owner)
                    if (capability == null) capability = runCatching { writes.verifyCreateSupport() }
                    requireNotNull(capability).getOrThrow()
                    val previous = row.previousLocalId?.let { parentId ->
                        val parent = dao.getInterviewById(parentId, owner) ?: error("Previous stage is missing from this account.")
                        parent.serverId ?: try { record(parentId, ancestors + id) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (e: Exception) { dao.getInterviewById(parentId, owner)?.serverId ?: throw e }
                    } ?: row.parentId
                    checkAccount(owner)
                    val response = writes.createInterview(InterviewRequests.create(row, previous))
                    checkAccount(owner)
                    val latest = dao.getInterviewById(id, owner) ?: error("Interview disappeared during upload.")
                    // A retry may return an earlier create payload; reconcile current edits with that response.
                    dao.update(latest.copy(serverId = response.id, applicationId = response.applicationId, parentId = response.parentId))
                    created++
                    val remainder = InterviewChanges.reconcileCreated(latest, response)
                    val pending = InterviewChanges.merge(latest.pendingChanges, remainder)
                    val fields = pending?.let { JsonParser.parseString(it).asJsonObject }
                    val stagePending = fields?.has("stageId") == true || fields?.has("stageName") == true
                    val methodPending = fields?.has("stageMethodId") == true || fields?.has("stageMethodName") == true
                    row = latest.copy(serverId = response.id, applicationId = response.applicationId,
                        parentId = response.parentId, serverStageId = if (stagePending) latest.serverStageId else response.stage?.id,
                        serverMethodId = if (methodPending) latest.serverMethodId else response.stageMethod?.id,
                        serverMethodName = if (methodPending) latest.serverMethodName else response.stageMethod?.method,
                        pendingChanges = pending, syncIssue = null)
                    dao.update(row)
                }
                val serverId = requireNotNull(row.serverId)
                row.pendingChanges?.let { pending ->
                    checkAccount(owner)
                    authenticate(owner)
                    checkAccount(owner)
                    if (capability == null) capability = runCatching { writes.verifyCreateSupport() }
                    requireNotNull(capability).getOrThrow()
                    val response = writes.patchInterview(serverId, JsonParser.parseString(pending).asJsonObject)
                    checkAccount(owner)
                    val latest = dao.getInterviewById(id, owner) ?: error("Interview disappeared during update.")
                    // Only acknowledge the revision whose exact patch was sent.
                    if (latest.localRevision == row.localRevision) {
                        dao.update(latest.copy(pendingChanges = null, syncIssue = null,
                            applicationId = response.applicationId ?: latest.applicationId,
                            parentId = response.parentId ?: latest.parentId,
                            serverStageId = response.stage?.id ?: latest.serverStageId,
                            serverMethodId = response.stageMethod?.id ?: latest.serverMethodId,
                            serverMethodName = response.stageMethod?.method ?: latest.serverMethodName,
                            stageSelectionChanged = false, methodSelectionChanged = false))
                    }
                    updated++
                }
                attempted[id] = Result.success(serverId)
                return serverId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                attempted[id] = Result.failure(e)
                checkAccount(owner)
                dao.getInterviewById(id, owner)?.let { dao.update(it.copy(syncIssue = e.message ?: "Sync needs retry")) }
                throw e
            }
        }
        val queue = if (onlyId == null) dao.pending(owner) else listOfNotNull(dao.getInterviewById(onlyId, owner))
        for (row in queue) {
            try { record(row.id, emptySet()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed++ }
        }
        return PendingSyncResult(created, updated, failed, dao.pending(owner).size)
    }
}
