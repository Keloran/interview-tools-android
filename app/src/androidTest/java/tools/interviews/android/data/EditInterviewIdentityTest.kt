package tools.interviews.android.data

import android.content.Intent
import tools.interviews.android.R
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import tools.interviews.android.data.api.*
import tools.interviews.android.data.account.AccountOwners
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.EditInterviewActivity
import tools.interviews.android.InterviewApplication
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewStage
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EditInterviewIdentityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as InterviewApplication
    private val ids = mutableListOf<Long>()
    private val companies = mutableListOf<Long>()
    @After fun cleanup() = runBlocking {
        ids.forEach { app.database.interviewDao().deleteById(it) }
        companies.forEach { app.database.companyDao().deleteById(it) }
    }

    @Test fun editingSyncedNotesKeepsServerIdentityAndHiddenFieldsAfterReload() = runBlocking {
        editAndReload(710001)
    }
    @Test fun editingLocalDraftKeepsItLocalAndPreservesItsCompany() = runBlocking {
        editAndReload(null)
    }
    private suspend fun editAndReload(serverId: Int?) {
        val company = app.database.companyDao().insert(Company(serverId = if (serverId != null) 710 else null, name = "Edit test ${UUID.randomUUID()}"))
        companies += company
        val original = Interview(companyId = company, serverId = serverId, companyName = "Example",
            jobTitle = "Engineer", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1),
            clientCompany = "Private client", metadataJSON = "{\"futureField\":\"keep\"}",
            interviewDate = LocalDateTime.of(2026, 2, 1, 10, 30),
            deadline = LocalDateTime.of(2026, 2, 2, 14, 15), link = "https://example.com/meeting",
            jobListing = "https://example.com/job", notes = "Original notes",
            applicationId = 77, parentId = 100, durationMinutes = 45, serverStageId = 1,
            serverMethodId = 50, serverMethodName = "Zoom", stageRecognized = true,
            method = tools.interviews.android.model.InterviewMethod.VIDEO_CALL)
        val id = app.database.interviewDao().insert(original)
        ids += id
        val intent = Intent(context, EditInterviewActivity::class.java)
            .putExtra(EditInterviewActivity.EXTRA_INTERVIEW_ID, id)
        ActivityScenario.launch<EditInterviewActivity>(intent).use { scenario ->
            val loaded = CountDownLatch(1)
            scenario.onActivity { activity ->
                val notes = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNotes)
                if (notes.text.toString() == "Original notes") loaded.countDown()
                else notes.doAfterTextChanged { if (it.toString() == "Original notes") loaded.countDown() }
            }
            assertTrue("Interview form did not load", loaded.await(10, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNotes).setText("Edited notes")
                activity.findViewById<android.view.View>(R.id.buttonSave).performClick()
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var edited = app.database.interviewDao().getInterviewById(id)!!
            while (edited.notes != "Edited notes" && System.nanoTime() < deadline) {
                kotlinx.coroutines.delay(20)
                edited = app.database.interviewDao().getInterviewById(id)!!
            }
            assertEquals(original.copy(id = id, notes = "Edited notes"), edited)
            val pending = app.database.interviewDao().getUnsyncedInterviews()
            assertEquals(serverId == null, pending.any { it.id == id })
            if (serverId != null) {
                val remote = object : SyncRemote {
                    override fun checkSession() {}
                    override suspend fun companies() = listOf(APICompany(710, "Example"))
                    override suspend fun interviews(take: Int, skip: Int) = listOf(APIInterview(
                        id = serverId, jobTitle = "Engineer", company = APICompany(710, "Example"),
                        applicationDate = "2026-01-01", stage = APIStage(1, "Applied"), stageMethod = null,
                        date = null, deadline = null, outcome = "AWAITING_RESPONSE", interviewer = null,
                        clientCompany = "Private client", notes = "Edited notes", metadata = null, link = null))
                    override suspend fun interview(id: Int): APIInterview? = null
                }
                val sync = SyncService(app.database.interviewDao(), app.database.companyDao(),
                    currentAccount = { AccountOwners.GUEST }, remoteFactory = { remote },
                    transaction = { block -> app.database.withTransaction { block() } })
                sync.syncAll()
                assertNull(sync.syncError.value)
                assertNotNull(sync.lastSyncDate.value)
                val matching = app.database.interviewDao().getAllInterviews().first().filter { it.serverId == serverId }
                assertEquals(1, matching.size)
                assertEquals(id, matching.single().id)
                // The edited row never enters the POST queue; sync updates its existing identity.
                assertFalse(app.database.interviewDao().getUnsyncedInterviews().any { it.id == id })
            }
        }
    }
}
