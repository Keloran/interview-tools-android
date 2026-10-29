package tools.interviews.android.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import java.io.IOException
import java.time.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.api.*
import tools.interviews.android.model.*
import tools.interviews.android.reminders.*

@RunWith(AndroidJUnit4::class)
class ReflectionPersistenceTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val name = "reflection-${UUID.randomUUID()}"
    private lateinit var db: InterviewDatabase
    private val gate = AccountOperationGate()
    private val owner = MutableStateFlow("a")

    @Before
    fun setup() {
        open()
    }

    private fun open() {
        db =
            Room.databaseBuilder(context, InterviewDatabase::class.java, name)
                .addMigrations(*InterviewDatabase.ALL_MIGRATIONS)
                .build()
    }

    @After
    fun cleanup() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun repository() =
        InterviewRepository(
            db.interviewDao(),
            db.companyDao(),
            gate,
            owner,
            transaction = { block -> db.withTransaction { block() } },
        )

    private fun row() =
        Interview(
            id = 1,
            ownerId = "a",
            serverId = 10,
            jobTitle = "Engineer",
            companyName = "Example",
            stage = InterviewStage.FIRST_STAGE,
            applicationDate = LocalDate.of(2026, 1, 1),
            interviewInstant = Instant.parse("2026-01-02T10:00:00Z"),
            applicationId = 7,
        )

    private fun api() =
        APIInterview(
            10,
            "Engineer",
            null,
            APICompany(1, "Example"),
            null,
            APIStage(2, "First Stage"),
            null,
            "2026-01-01",
            null,
            null,
            "SCHEDULED",
            null,
            null,
            null,
            7,
        )

    private fun storyBody(title: String) =
        StoryFields.body(mapOf("title" to title, "action" to "Led the team"), "leadership")

    private inner class Stories : StoryWrites {
        val records = mutableMapOf<Int, JsonObject>()
        val receipts = mutableMapOf<String, Int>()
        val mutations = mutableListOf<String>()
        var loseResponse = false
        var failUpdate = false
        var afterCreate: (() -> Unit)? = null

        override suspend fun createStory(body: JsonObject): JsonObject {
            val mutation = body.get("clientMutationId").asString
            mutations += mutation
            val key = "${owner.value}:$mutation"
            val id =
                receipts.getOrPut(key) { (records.size + 1).also { records[it] = body.deepCopy() } }
            afterCreate?.invoke()
            if (loseResponse) {
                loseResponse = false
                throw IOException("Response lost")
            }
            return JsonObject().apply { addProperty("id", id) }
        }

        override suspend fun updateStory(id: Int, body: JsonObject) {
            if (failUpdate) throw IOException("Offline update")
            records[id] = body.deepCopy()
        }
    }

    private fun saver(remote: StoryWrites) =
        StorySaveService(db.toolDraftDao(), gate, remote, { owner.value }, {})

    @Test
    fun lostStoryCreateResponseRetainsUuidAcrossRestartAndAppliesNewerText() = runBlocking {
        val remote = Stories().apply { loseResponse = true }
        assertTrue(
            runCatching { saver(remote).save("a", "story:new", null, storyBody("Before")) }
                .isFailure
        )
        db.close()
        open()
        assertNotNull(db.toolDraftDao().get("a", "story:new"))
        val id = saver(remote).save("a", "story:new", null, storyBody("Edited after timeout"))
        assertEquals(1, remote.records.size)
        assertEquals(1, remote.mutations.distinct().size)
        assertEquals("Edited after timeout", remote.records[id]!!.get("title").asString)
        assertNull(db.toolDraftDao().get("a", "story:new"))
    }

    @Test
    fun storyIdentityIsSavedBeforeAFollowUpEditFails() = runBlocking {
        val remote = Stories().apply { failUpdate = true }
        assertTrue(
            runCatching { saver(remote).save("a", "story:new", null, storyBody("Example")) }
                .isFailure
        )
        val draft =
            JsonParser.parseString(db.toolDraftDao().get("a", "story:new")!!.json).asJsonObject
        assertEquals(1, draft.get("serverId").asInt)
        db.close()
        open()
        remote.failUpdate = false
        assertEquals(1, saver(remote).save("a", "story:new", null, storyBody("Latest")))
        assertEquals(1, remote.mutations.size)
    }

    @Test
    fun accountSwitchCannotAcknowledgeOrReplayAnotherAccountsStoryDraft() = runBlocking {
        val remote = Stories().apply { afterCreate = { owner.value = "b" } }
        assertTrue(
            runCatching { saver(remote).save("a", "story:new", null, storyBody("Private")) }
                .exceptionOrNull() is CancellationException
        )
        assertNotNull(db.toolDraftDao().get("a", "story:new"))
        assertNull(db.toolDraftDao().get("b", "story:new"))
        remote.afterCreate = null
        saver(remote).save("b", "story:new", null, storyBody("Other account"))
        assertEquals(2, remote.records.size)
        assertEquals(2, remote.mutations.distinct().size)
        assertNotNull(db.toolDraftDao().get("a", "story:new"))
    }

    @Test
    fun debriefFeedbackAndPreparationRetryIndependentlyAcrossRestart() = runBlocking {
        val original = row()
        db.interviewDao().insert(original)
        val reflection =
            StageReflection.debrief(mapOf("questionsAsked" to "Explain indexes"), 3, null)
        val feedback = StageReflection.feedback("SPECIFIC", "More SQL experience")
        val prep = StageReflection.prep("Research company", listOf(2, 3))
        repository()
            .saveChanges(
                original,
                original.copy(
                    debriefJSON = reflection,
                    feedbackJSON = feedback,
                    prepJSON = prep,
                    debriefedAt = "2026-01-02T12:00:00Z",
                ),
            )
        var patches = 0
        var preparations = 0
        var offline = true
        val remote =
            object : SyncWrites {
                override suspend fun createInterview(
                    request: CreateInterviewRequest
                ): APIInterview = error("Existing interview must not be created again")

                override suspend fun updateInterview(
                    id: Int,
                    request: UpdateInterviewRequest,
                ): APIInterview = error("Use patch")

                override suspend fun patchInterview(id: Int, patch: JsonObject): APIInterview {
                    patches++
                    assertEquals(
                        "Explain indexes",
                        patch.getAsJsonObject("debrief").get("questionsAsked").asString,
                    )
                    assertEquals("SPECIFIC", patch.getAsJsonObject("feedback").get("type").asString)
                    return api()
                }

                override suspend fun updatePrep(id: Int, body: JsonObject) {
                    preparations++
                    if (offline) throw IOException("Offline")
                    assertEquals(listOf(2, 3), body.getAsJsonArray("storyIds").map { it.asInt })
                }
            }
        fun engine() = PendingSyncEngine(db.interviewDao(), remote, { owner.value })
        assertEquals(1, engine().sync("a").failed)
        assertNull(db.interviewDao().getInterviewById(1, "a")!!.pendingChanges)
        db.close()
        open()
        offline = false
        assertEquals(0, engine().sync("a").remaining)
        assertEquals(1, patches)
        assertEquals(2, preparations)
        val saved = db.interviewDao().getInterviewById(1, "a")!!
        assertEquals(reflection, saved.debriefJSON)
        assertEquals(prep, saved.prepJSON)
        assertNull(saved.pendingPrepJSON)
    }

    @Test
    fun refreshingCannotOverwritePendingReflectionOrPreparation() = runBlocking {
        val original =
            row()
                .copy(
                    debriefJSON =
                        StageReflection.debrief(
                            mapOf("questionsAsked" to "Local question"),
                            null,
                            null,
                        ),
                    feedbackJSON = StageReflection.feedback("NONE", ""),
                    prepJSON = StageReflection.prep("Local prep", listOf(3)),
                    pendingPrepJSON = StageReflection.prep("Local prep", listOf(3)),
                    pendingChanges = "{\"debrief\":{},\"feedback\":{}}",
                )
        db.interviewDao().insert(original)
        val remote =
            object : ReflectionRemote {
                override suspend fun reflectionInterview(id: Int) =
                    api().copy(questionsAsked = "Server question", feedbackType = "SPECIFIC")

                override suspend fun prep(id: Int) =
                    JsonParser.parseString(StageReflection.prep("Server prep", emptyList()))
                        .asJsonObject
            }
        ReflectionStore(db.interviewDao(), gate, remote, { owner.value }, {}).refresh("a", 1)
        val saved = db.interviewDao().getInterviewById(1, "a")!!
        assertEquals(original.debriefJSON, saved.debriefJSON)
        assertEquals(original.feedbackJSON, saved.feedbackJSON)
        assertEquals(original.prepJSON, saved.prepJSON)
    }

    @Test
    fun notificationPermissionAccountAndDebriefStateAreRecheckedBeforePosting() = runBlocking {
        db.interviewDao().insert(row())
        var allowed = false
        var posted = 0
        val runner =
            DebriefReminderRunner(
                db.interviewDao(),
                gate,
                { owner.value },
                { allowed },
                {
                    posted++
                    true
                },
            )
        val now = Instant.parse("2026-01-03T00:00:00Z")
        assertFalse(runner.run("a", 1, now))
        assertEquals(0, posted)
        allowed = true
        owner.value = "b"
        assertFalse(runner.run("a", 1, now))
        assertEquals(0, posted)
        owner.value = "a"
        assertTrue(runner.run("a", 1, now))
        assertEquals(1, posted)
        db.close()
        open()
        val restarted =
            DebriefReminderRunner(
                db.interviewDao(),
                gate,
                { owner.value },
                { true },
                {
                    posted++
                    true
                },
            )
        assertFalse(restarted.run("a", 1, now))
        assertEquals(1, posted)
        val saved = db.interviewDao().getInterviewById(1, "a")!!
        db.interviewDao()
            .update(saved.copy(debriefPromptedFor = null, debriefedAt = now.toString()))
        assertFalse(restarted.run("a", 1, now))
        assertEquals(1, posted)
    }

    @Test
    fun failedNotificationPostsDoNotMarkAnInterviewAsPrompted() = runBlocking {
        db.interviewDao().insert(row())
        val runner =
            DebriefReminderRunner(db.interviewDao(), gate, { owner.value }, { true }, { false })
        assertFalse(runner.run("a", 1, Instant.parse("2026-01-03T00:00:00Z")))
        assertNull(db.interviewDao().getInterviewById(1, "a")!!.debriefPromptedFor)
    }

    @Test
    fun accountCleanupRemovesItsPrivateDraftsWithoutTouchingAnotherAccount() = runBlocking {
        db.toolDraftDao().put(ToolDraft("a", "story:new", "{\"private\":true}"))
        db.toolDraftDao().put(ToolDraft("b", "story:new", "{\"other\":true}"))
        db.clearAccountCache("a", false)
        assertNull(db.toolDraftDao().get("a", "story:new"))
        assertNotNull(db.toolDraftDao().get("b", "story:new"))
    }
}
