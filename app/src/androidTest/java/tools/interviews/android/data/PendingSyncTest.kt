package tools.interviews.android.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.api.*
import tools.interviews.android.model.*
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PendingSyncTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "pending-sync-${UUID.randomUUID()}"
    private lateinit var db: InterviewDatabase
    private val owner = MutableStateFlow("account-a")
    private val gate = AccountOperationGate()
    private val writes = Writes()
    @Before fun setup() { reopen() }
    private fun reopen() { db = Room.databaseBuilder(context, InterviewDatabase::class.java, name).addMigrations(*InterviewDatabase.ALL_MIGRATIONS).build() }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private fun repository() = InterviewRepository(db.interviewDao(), db.companyDao(), gate, owner,
        transaction = { block -> db.withTransaction { block() } })
    private fun row(id: Long = 1, serverId: Int? = 10) = Interview(id = id, serverId = serverId, ownerId = owner.value,
        jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.FIRST_STAGE,
        method = InterviewMethod.VIDEO_CALL, applicationDate = LocalDate.of(2026, 1, 1),
        interviewInstant = Instant.parse("2026-06-01T09:00:00Z"), notes = "Original", applicationId = 77)
    private suspend fun seed(value: Interview = row()): Interview {
        db.interviewDao().insert(value)
        value.serverId?.let { writes.byId[it] = api(value, it) }
        return value
    }
    private fun engine() = PendingSyncEngine(db.interviewDao(), writes, { owner.value })
    private fun api(row: Interview, id: Int) = APIInterview(id, row.jobTitle, row.interviewer,
        APICompany(1, row.companyName), row.clientCompany, APIStage(2, row.stage.displayName), APIStageMethod(5, "Link"),
        row.applicationDate.toString(), row.interviewInstant?.toString(), row.deadlineInstant?.toString(),
        row.outcome.name, row.notes, APIMetadata(row.jobListing, null), row.link, row.applicationId ?: 77, row.parentId, row.durationMinutes ?: 60)

    @Test fun offlineEditAndFailureSurviveRelaunchThenAcknowledgeOnlyOnce() = runBlocking {
        val original = seed()
        repository().saveChanges(original, original.copy(notes = "Offline edit"))
        writes.failPatch = true
        assertEquals(1, engine().sync(owner.value).failed)
        db.close(); reopen()
        var saved = db.interviewDao().getInterviewById(1, owner.value)!!
        assertEquals("Offline edit", saved.notes)
        assertNotNull(saved.pendingChanges)
        assertNotNull(saved.syncIssue)
        writes.failPatch = false
        assertEquals(0, engine().sync(owner.value).failed)
        saved = db.interviewDao().getInterviewById(1, owner.value)!!
        assertNull(saved.pendingChanges)
        assertNull(saved.syncIssue)
        assertEquals("Offline edit", writes.byId[10]!!.notes)
        val calls = writes.patches.size
        assertEquals(0, engine().sync(owner.value).updated)
        assertEquals(calls, writes.patches.size)
    }
    @Test fun lostCreateResponseUsesTheSameIdentityAcrossRestartAndLaterEdits() = runBlocking {
        val draft = row(id = 0, serverId = null)
        val id = repository().insert(draft)
        writes.loseCreateResponse = true
        assertEquals(1, engine().sync(owner.value).failed)
        val mutation = db.interviewDao().getInterviewById(id, owner.value)!!.clientMutationId
        assertNotNull(mutation)
        db.close(); reopen()
        val old = db.interviewDao().getInterviewById(id, owner.value)!!
        repository().saveChanges(old, old.copy(notes = "Edited after timeout", serverMethodId = 50, serverMethodName = "Teams", methodSelectionChanged = true))
        writes.failPatch = true
        assertEquals(1, engine().sync(owner.value).failed)
        val waiting = db.interviewDao().getInterviewById(id, owner.value)!!
        assertNotNull(waiting.serverId)
        assertEquals("Teams", waiting.methodLabel)
        assertEquals(50, waiting.serverMethodId)
        writes.failPatch = false
        assertEquals(0, engine().sync(owner.value).failed)
        val saved = db.interviewDao().getInterviewById(id, owner.value)!!
        assertEquals(mutation, saved.clientMutationId)
        assertEquals(1, writes.receipts.size)
        assertEquals(listOf(mutation, mutation), writes.creates.map { it.clientMutationId })
        assertNotNull(saved.serverId)
        assertNull(saved.pendingChanges)
        assertEquals("Edited after timeout", writes.byId[saved.serverId]!!.notes)
    }
    @Test fun accountSwitchDuringCreateDoesNotReplayOrRevealAnotherOwnersQueue() = runBlocking {
        val id = repository().insert(row(id = 0, serverId = null))
        writes.afterCreate = { owner.value = "account-b" }
        assertTrue(runCatching { engine().sync("account-a") }.exceptionOrNull() is CancellationException)
        assertTrue(db.interviewDao().pending("account-b").isEmpty())
        assertNull(db.interviewDao().getInterviewById(id, "account-b"))
        assertEquals(0, engine().sync("account-b").created)
        owner.value = "account-a"; writes.afterCreate = null
        assertEquals(0, engine().sync("account-a").failed)
        assertEquals(1, writes.receipts.size)
        assertNotNull(db.interviewDao().getInterviewById(id, "account-a")!!.serverId)
    }
    @Test fun missingServerSupportLeavesTheDraftWithoutAnyCreateOrPatch() = runBlocking {
        val id = repository().insert(row(id = 0, serverId = null))
        writes.supported = false
        assertEquals(1, engine().sync(owner.value).failed)
        assertTrue(writes.creates.isEmpty())
        assertTrue(writes.patches.isEmpty())
        assertNull(db.interviewDao().getInterviewById(id, owner.value)!!.serverId)
    }
    @Test fun aLateAcknowledgementDoesNotClearANewerRevision() = runBlocking {
        val original = seed()
        repository().saveChanges(original, original.copy(notes = "First edit"))
        writes.afterPatch = {
            val current = db.interviewDao().getInterviewById(1, owner.value)!!
            db.interviewDao().update(current.copy(notes = "Second edit", localRevision = current.localRevision + 1,
                pendingChanges = "{\"notes\":\"Second edit\"}"))
        }
        val result = engine().sync(owner.value)
        assertEquals(1, result.remaining)
        assertEquals("Second edit", db.interviewDao().getInterviewById(1, owner.value)!!.notes)
        assertNotNull(db.interviewDao().getInterviewById(1, owner.value)!!.pendingChanges)
        writes.afterPatch = null
        assertEquals(0, engine().sync(owner.value).remaining)
        assertEquals("Second edit", writes.byId[10]!!.notes)
    }
    @Test fun aFailedIndependentUpdateDoesNotBlockAChildWhoseParentAlreadyExists() = runBlocking {
        val parent = seed()
        repository().saveChanges(parent, parent.copy(notes = "Parent edit"))
        val child = row(2, null).copy(previousLocalId = 1)
        seed(child)
        writes.failPatch = true
        val result = engine().sync(owner.value)
        assertEquals(1, result.failed)
        assertEquals(1, result.created)
        assertEquals(10, writes.creates.single().previousInterviewId)
        assertNotNull(db.interviewDao().getInterviewById(2, owner.value)!!.serverId)
        assertNotNull(db.interviewDao().getInterviewById(1, owner.value)!!.pendingChanges)
    }
    @Test fun editQueuedDuringPullPreservesNewServerFieldsAndOnlySendsTheUserChange() = runBlocking {
        val before = seed()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = object : SyncRemote {
            override fun checkSession() {}
            override suspend fun companies() = listOf(APICompany(1, "Example"))
            override suspend fun interviews(take: Int, skip: Int): List<APIInterview> {
                entered.complete(Unit); release.await()
                return listOf(api(before.copy(jobTitle = "New server title", outcome = InterviewOutcome.NO_RESPONSE, notes = "New server notes"), 10))
            }
            override suspend fun interview(id: Int): APIInterview? = null
        }
        val sync = SyncService(db.interviewDao(), db.companyDao(), writes = writes, gate = gate,
            currentAccount = { owner.value }, remoteFactory = { remote }, transaction = { block -> db.withTransaction { block() } })
        withTimeout(10000) {
            val download = launch { sync.syncAll() }
            entered.await()
            val edit = launch { repository().saveChanges(before, before.copy(notes = "My latest note")) }
            yield(); release.complete(Unit)
            download.join(); edit.join()
        }
        val saved = db.interviewDao().getInterviewById(1, owner.value)!!
        assertEquals("New server title", saved.jobTitle)
        assertEquals(InterviewOutcome.NO_RESPONSE, saved.outcome)
        assertEquals("My latest note", saved.notes)
        val patch = com.google.gson.JsonParser.parseString(saved.pendingChanges).asJsonObject
        assertEquals(setOf("notes"), patch.keySet())
    }
    @Test fun partialFailuresNeverAdvanceTheLastSuccessfulSyncDate() = runBlocking {
        val original = seed()
        repository().saveChanges(original, original.copy(notes = "Offline"))
        writes.failPatch = true
        val sync = SyncService(db.interviewDao(), db.companyDao(), writes = writes, gate = gate, currentAccount = { owner.value })
        sync.syncAll()
        assertNotNull(sync.syncError.value)
        assertNull(sync.lastSyncDate.value)
        assertEquals(1, sync.pendingResult.value.failed)
    }
    @Test fun explicitClearsSurviveTheQueueAndReachTheServer() = runBlocking {
        val original = seed(row().copy(link = "https://example.com", interviewer = "Jane"))
        repository().saveChanges(original, original.copy(notes = null, link = null, interviewer = null))
        assertEquals(0, engine().sync(owner.value).failed)
        val patch = writes.patches.single()
        assertTrue(patch.get("link").isJsonNull)
        assertTrue(patch.get("interviewer").isJsonNull)
        assertEquals("", patch.get("notes").asString)
        assertNull(db.interviewDao().getInterviewById(1, owner.value)!!.pendingChanges)
    }

    @Test fun recruiterChangesSurviveRestartAndRetryWithoutRepostingInterview() = runBlocking {
        val original = seed()
        val contact = RecruiterContact(true, "Sam", "sam@example.com", "").json()
        repository().saveChanges(original, original.copy(recruiterContactJSON=contact))
        writes.failRecruiter = true
        assertEquals(1, engine().sync(owner.value).failed)
        db.close(); reopen()
        assertEquals(contact, db.interviewDao().getInterviewById(1,owner.value)!!.pendingRecruiterJSON)
        writes.failRecruiter = false
        assertEquals(0, engine().sync(owner.value).failed)
        assertNull(db.interviewDao().getInterviewById(1,owner.value)!!.pendingRecruiterJSON)
        assertEquals("Sam", writes.recruiterCalls.last().second.get("recruiterName").asString)
        assertTrue(writes.creates.isEmpty())
        assertTrue(writes.patches.isEmpty())
    }
    @Test fun acknowledgedCreateSurvivesRecruiterFailureAndDoesNotCreateAgain() = runBlocking {
        val contact = RecruiterContact(true,"Sam","sam@example.com","").json()
        val id = repository().insert(row(id=0,serverId=null).copy(recruiterContactJSON=contact,pendingRecruiterJSON=contact))
        writes.failRecruiter = true
        assertEquals(1,engine().sync(owner.value).failed)
        assertNotNull(db.interviewDao().getInterviewById(id,owner.value)!!.serverId)
        db.close(); reopen(); writes.failRecruiter=false
        assertEquals(0,engine().sync(owner.value).failed)
        assertEquals(1,writes.creates.size)
        assertEquals(77,writes.recruiterCalls.last().first)
    }
    @Test fun latestApplicationContactWinsAcrossStagesWithoutClearingOtherEdits() = runBlocking {
        val old = RecruiterContact(true,"Old","","").json()
        val first = seed(row().copy(recruiterContactJSON=old,pendingRecruiterJSON=old,pendingChanges="{\"notes\":\"Offline notes\"}"))
        val second = seed(row(id=2,serverId=11).copy(recruiterContactJSON=old))
        val fresh = RecruiterContact(true,"New","","").json()
        repository().saveChanges(second,second.copy(recruiterContactJSON=fresh))
        assertEquals(fresh,db.interviewDao().getInterviewById(first.id,owner.value)!!.recruiterContactJSON)
        assertNull(db.interviewDao().getInterviewById(first.id,owner.value)!!.pendingRecruiterJSON)
        assertNotNull(db.interviewDao().getInterviewById(first.id,owner.value)!!.pendingChanges)
        assertEquals(0,engine().sync(owner.value).failed)
        assertEquals(1,writes.recruiterCalls.size)
        assertEquals("New",writes.recruiterCalls.single().second.get("recruiterName").asString)
    }
    @Test fun lateRecruiterAcknowledgementCannotClearANewerContact() = runBlocking {
        val original = seed()
        val first = RecruiterContact(true,"First","","").json()
        repository().saveChanges(original,original.copy(recruiterContactJSON=first))
        writes.afterRecruiter = {
            val latest = db.interviewDao().getInterviewById(1,owner.value)!!
            db.interviewDao().update(latest.copy(recruiterContactJSON=RecruiterContact(true,"Second","","").json(), pendingRecruiterJSON=RecruiterContact(true,"Second","","").json(),localRevision=latest.localRevision+1))
        }
        assertEquals(1,engine().sync(owner.value).remaining)
        assertNotNull(db.interviewDao().getInterviewById(1,owner.value)!!.pendingRecruiterJSON)
        writes.afterRecruiter=null
        assertEquals(0,engine().sync(owner.value).remaining)
        assertEquals("Second",writes.recruiterCalls.last().second.get("recruiterName").asString)
    }
    @Test fun switchingAccountsDuringRecruiterRequestRetainsOnlyTheOriginalOwnersQueue() = runBlocking {
        val original = seed()
        repository().saveChanges(original,original.copy(recruiterContactJSON=RecruiterContact(true,"Private","","").json()))
        writes.afterRecruiter={ owner.value="account-b" }
        try { engine().sync("account-a"); fail("Stale result accepted") } catch (_: CancellationException) { }
        assertNotNull(db.interviewDao().getInterviewById(1,"account-a")!!.pendingRecruiterJSON)
        assertEquals(0,engine().sync("account-b").remaining)
        assertEquals(1,writes.recruiterCalls.size)
    }

    @Test fun recruiterEditsRecoverMissingApplicationIdentityFromOwnedInterview() = runBlocking {
        val original = seed(row().copy(applicationId=null))
        repository().saveChanges(original,original.copy(recruiterContactJSON=RecruiterContact(true,"Sam").json()))
        assertEquals(0,engine().sync(owner.value).failed)
        assertEquals(77,db.interviewDao().getInterviewById(1,owner.value)!!.applicationId)
        assertNull(db.interviewDao().getInterviewById(1,owner.value)!!.pendingRecruiterJSON)
        assertEquals(77,writes.recruiterCalls.single().first)
    }
    private inner class Writes : SyncWrites {
        override suspend fun applicationIdForInterview(id: Int): Int? = byId[id]?.applicationId
        val recruiterCalls=mutableListOf<Pair<Int,JsonObject>>()
        var failRecruiter=false
        var afterRecruiter: (suspend () -> Unit)?=null
        override suspend fun updateRecruiter(id: Int, body: JsonObject) {
            recruiterCalls += id to body.deepCopy()
            if (failRecruiter) throw IOException("Offline recruiter")
            afterRecruiter?.invoke()
        }
        val byId = mutableMapOf<Int, APIInterview>()
        val receipts = mutableMapOf<String, Int>()
        val creates = mutableListOf<CreateInterviewRequest>()
        val patches = mutableListOf<JsonObject>()
        var supported = true
        var failPatch = false
        var loseCreateResponse = false
        var afterCreate: (() -> Unit)? = null
        var afterPatch: (suspend () -> Unit)? = null
        override suspend fun verifyCreateSupport() { check(supported) { "Service update pending" } }
        override suspend fun createInterview(request: CreateInterviewRequest): APIInterview {
            creates += request
            val key = "${owner.value}:${requireNotNull(request.clientMutationId)}"
            val id = receipts[key] ?: (100 + receipts.size).also { assigned ->
                receipts[key] = assigned
                byId[assigned] = APIInterview(assigned, request.jobTitle, request.interviewer, APICompany(1, request.companyName),
                    request.clientCompany, APIStage(2, request.stage), APIStageMethod(request.stageMethodId ?: 5, "Link"),
                    "2026-01-01", request.date, request.deadline,
                    if (request.stage == "Applied") "AWAITING_RESPONSE" else "SCHEDULED", request.notes,
                    APIMetadata(request.jobPostingLink, null), request.interviewLink, 77, request.previousInterviewId, request.durationMinutes ?: 60)
            }
            afterCreate?.invoke()
            if (loseCreateResponse) { loseCreateResponse = false; throw IOException("Response lost") }
            return requireNotNull(byId[id])
        }
        override suspend fun patchInterview(id: Int, patch: JsonObject): APIInterview {
            patches += patch.deepCopy()
            if (failPatch) throw IOException("Offline")
            var value = requireNotNull(byId[id])
            if (patch.has("notes")) value = value.copy(notes = patch.get("notes").asString)
            if (patch.has("outcome")) value = value.copy(outcome = patch.get("outcome").asString)
            if (patch.has("link")) value = value.copy(link = patch.get("link").takeUnless { it.isJsonNull }?.asString)
            if (patch.has("interviewer")) value = value.copy(interviewer = patch.get("interviewer").takeUnless { it.isJsonNull }?.asString)
            if (patch.has("stageMethodId")) value = value.copy(stageMethod = APIStageMethod(patch.get("stageMethodId").asInt, "Teams"))
            byId[id] = value
            afterPatch?.invoke()
            return value
        }
        override suspend fun updateInterview(id: Int, request: UpdateInterviewRequest): APIInterview = error("Use explicit patches")
    }
}
