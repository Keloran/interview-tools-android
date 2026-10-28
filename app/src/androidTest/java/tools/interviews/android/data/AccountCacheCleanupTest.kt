package tools.interviews.android.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview
import tools.interviews.android.model.InterviewStage
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AccountCacheCleanupTest {
    private lateinit var database: InterviewDatabase
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, InterviewDatabase::class.java).build()
    }
    @After fun close() { database.close() }

    private suspend fun seed() {
        database.companyDao().insert(Company(id = 1, serverId = 10, ownerId = "account-a", name = "Shared company"))
        database.companyDao().insert(Company(id = 2, serverId = 20, ownerId = "account-a", name = "Account-only company"))
        database.interviewDao().insert(Interview(id = 1, ownerId = "account-a", serverId = 100, companyId = 1,
            companyName = "Shared company", jobTitle = "Synced role", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1)))
        database.interviewDao().insert(Interview(id = 2, companyId = 1,
            companyName = "Shared company", jobTitle = "Guest role", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 2)))
    }

    @Test fun deletingAccountPreservesGuestRowsAndDetachesTheirServerCompany() = runBlocking {
        seed()
        database.clearAccountCache("account-a", clearGuests = false)
        assertNull(database.interviewDao().getInterviewById(1, "account-a"))
        assertEquals("Guest role", database.interviewDao().getInterviewById(2)!!.jobTitle)
        assertNull(database.companyDao().getCompanyById(1, "legacy")!!.serverId)
        assertNull(database.companyDao().getCompanyById(2, "account-a"))
        assertTrue(database.interviewDao().getAllServerIds("account-a").isEmpty())
        assertTrue(database.companyDao().getAllServerIds("account-a").isEmpty())
        database.clearAccountCache("account-a", clearGuests = false) // cleanup retries are safe
        assertNotNull(database.interviewDao().getInterviewById(2))
    }

    @Test fun guestRemovalRequiresTheExplicitClearGuestsChoice() = runBlocking {
        seed()
        database.clearAccountCache("account-a", clearGuests = true)
        assertNull(database.interviewDao().getInterviewById(1, "account-a"))
        assertNull(database.interviewDao().getInterviewById(2))
        assertTrue(database.companyDao().getAllCompaniesList("account-a").isEmpty())
    }

    @Test fun providerExposesOnlyThePrivateExportFolder() {
        val directory = File(context.cacheDir, "account-exports").apply { mkdirs() }
        val csv = File(directory, "test.csv").apply { writeText("role,notes\nEngineer,Private\n") }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", csv)
            assertEquals("content", uri.scheme)
            assertEquals(csv.readText(), context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.exports", File(context.filesDir, "unrelated.txt"))
                fail("Unrelated app files must not be exposed")
            } catch (_: IllegalArgumentException) { /* expected */ }
        } finally { csv.delete() }
    }
}
